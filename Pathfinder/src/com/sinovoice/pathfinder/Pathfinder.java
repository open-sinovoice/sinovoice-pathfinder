package com.sinovoice.pathfinder;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import com.sinovoice.hcicloudsdk.api.hwr.HciCloudHwr;
import com.sinovoice.hcicloudsdk.common.HciErrorCode;
import com.sinovoice.hcicloudsdk.common.hwr.HwrRecogResult;
import com.sinovoice.hcicloudsdk.common.hwr.HwrRecogResultItem;
import com.sinovoice.pathfinder.hcicloud.hwr.HWRManager;
import com.sinovoice.pathfinder.hcicloud.hwr.HciCloudHwrHelper;
import com.sinovoice.pathfinder.hcicloud.hwr.OnHwrStateChangedListener;
import com.sinovoice.pathfinder.hcicloud.sys.HciCloudSysHelper;

public class Pathfinder extends InputMethodService implements OnHwrStateChangedListener{
    private static final String TAG = Pathfinder.class.getSimpleName();
    
    /**
     * 异步获取灵云授权后发送的消息的msg.what
     */
    public static final int MSG_WHAT_CHECK_AUTH_FINISH = 1;
    public static final int MSG_WHAT_ASR_RESULT = 2;
    
    private InputMethodManager mInputMethodManager;
    private MyHandler mHandler;
    
    //hcicloud sys
    private HciCloudSysHelper mCloudSysHelper;
    private boolean mHciSysInited;
    
    //hcicloud hwr
    private HciCloudHwrHelper mCLoudHwrHelper;
    private boolean mHciHwrInited;
    private HWRManager mHwrManager;

    /**
     * 输入法的键盘视图
     */
    private SVInputView mInputView;
    
    /**
     * 候选栏视图
     */
	private CandidateView mCandidateView;
	private CompletionInfo[] mCompletions;
	
	private StringBuilder mComposing = new StringBuilder();
    private boolean mCompletionOn;
    
    private String mWordSeparators;
    
    @Override
    public void onCreate() {
    	super.onCreate();
    	
        Log.i(TAG, "onCreate()");
        
        mInputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        mWordSeparators = getResources().getString(R.string.word_separators);
        mHandler = new MyHandler(this);
        
        mCloudSysHelper = HciCloudSysHelper.getInstance();
        mCLoudHwrHelper = HciCloudHwrHelper.getInstance();
        mHwrManager = HWRManager.instance();
        mHwrManager.init(this);
        
        //hcicloud sys init
        int errorCode = mCloudSysHelper.init(this);
        if (errorCode  == HciErrorCode.HCI_ERR_NONE) {
        	mHciSysInited = checkExpireTimeAndCapkeys();
        	Log.d(TAG, "mHciSysInited: " + mHciSysInited);
        }else{
        	Log.e(TAG, "sys init fatal. errorCode: " + errorCode);
        	return;
        }
        
        //hcicloud hwr init
        if(mHciSysInited){
        	initHwr();        	
        }else{
        	Log.w(TAG, "未调用initHwr()，灵云能力需要联网获取授权后，继续调用initHwr()");
        }
    }

    /**
     * 初始化灵云的hwr能力
     */
	private void initHwr() {
		int errorCode = mCLoudHwrHelper.init(getApplicationContext());
		if (errorCode  == HciErrorCode.HCI_ERR_NONE) {
			mHciHwrInited = true;
			Log.d(TAG, "mHciHwrInited: " + mHciHwrInited);
		}else{
			Log.e(TAG, "hwr init fatal. errorCode: " + errorCode);
			return;
		}
		
		//Hwr Manager
		mHwrManager.prepareRecog();
		mHwrManager.setOnHwrRecogResultChangedListener(this);
	}
	
	@Override
	public View onCreateInputView() {
		mInputView = (SVInputView) getLayoutInflater().inflate(R.layout.input, null);
		mInputView.init(this);

		return mInputView;
	}

    @Override
    public View onCreateCandidatesView() {
        mCandidateView = new CandidateView(this);
        mCandidateView.setService(this);
        mCandidateView.setHandler(mHandler);
        return mCandidateView;
    }
    
    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
    	super.onStartInputView(info, restarting);
    	
    	setCandidatesViewShown(true);
    }

    @Override
	public void onStartInput(EditorInfo attribute, boolean restarting) {
		super.onStartInput(attribute, restarting);
		
        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.
        mComposing.setLength(0);
        updateCandidates();
        
        mCompletionOn = false;
        mCompletions = null;
	}
	
    /**
     * Deal with the editor reporting movement of its cursor.
     */
    @Override 
    public void onUpdateSelection(int oldSelStart, int oldSelEnd,
            int newSelStart, int newSelEnd,
            int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
        
        // If the current selection in the text view changes, we should
        // clear whatever candidate text we have.
        if (mComposing.length() > 0 && (newSelStart != candidatesEnd
                || newSelEnd != candidatesEnd)) {
            mComposing.setLength(0);
            updateCandidates();
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.finishComposingText();
            }
        }
    }
    
    /**
     * This tells us about completions that the editor has determined based
     * on the current text in it.  We want to use this in fullscreen mode
     * to show the completions ourself, since the editor can not be seen
     * in that situation.
     */
    @Override 
    public void onDisplayCompletions(CompletionInfo[] completions) {
        if (mCompletionOn) {
            mCompletions = completions;
            if (completions == null) {
                setSuggestions(null, false, false);
                return;
            }
            
            List<String> stringList = new ArrayList<String>();
            for (int i = 0; i < completions.length; i++) {
                CompletionInfo ci = completions[i];
                if (ci != null) stringList.add(ci.getText().toString());
            }
            setSuggestions(stringList, true, true);
        }
    }

	@Override
	public void onFinishInput() {
        super.onFinishInput();
        
        // Clear current composing text and candidates.
        mComposing.setLength(0);
        updateCandidates();
        
        // We only hide the candidates window when finishing input on
        // a particular editor, to avoid popping the underlying application
        // up and down if the user is entering text into the bottom of
        // its window.
        setCandidatesViewShown(false);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		
		//屏幕方向切换时候，更加屏幕的方向设置手写的模式，横屏行写，竖屏叠写
		if(newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
				|| newConfig.orientation == Configuration.ORIENTATION_SQUARE){
			mHwrManager.setSpliteMode(HciCloudHwr.HCI_HWR_SPLIT_MODE_OVERLAP);
		}else if(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE){
			mHwrManager.setSpliteMode(HciCloudHwr.HCI_HWR_SPLIT_MODE_LINE);
		}else{
			//do nothing
		}
	}

	@Override
    public void onDestroy() {
        super.onDestroy();
        
        mInputMethodManager = null;
        
        // 首先release各个能力
        // hwr release
        mHwrManager.release();
        while(!mHwrManager.isReleaseSuccess()){
        	try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        }
        mHwrManager = null;
        
        mCLoudHwrHelper.release();
        mCLoudHwrHelper = null;
        
        //　系统release
        mCloudSysHelper.release();
        mCloudSysHelper = null;
        
        mHandler = null;
    }
    
    /**
     * 检查授权文件的过期时间，以及能力是否均存在
     * 
     * @return
     */
    private boolean checkExpireTimeAndCapkeys() {
        //检查授权文件的过期时间
        int errorCode = mCloudSysHelper.checkExpireTime();
        if (errorCode != HciCloudSysHelper.ERRORCODE_NONE) {
            Log.w(TAG, "checkExpireTime error, errorCode = " + errorCode);
            checkAuthByThread();
            return false;
        } else {
            // 检查授权成功
        }
        
        //检查本地授权文件中是否包含本应用使用的所有capkeys
        errorCode = mCloudSysHelper.checkCapkeysEnable();
        if (errorCode != HciCloudSysHelper.ERRORCODE_NONE) {
            Log.w(TAG, "checkCapkeysEnable error, errorCode = " + errorCode);
            checkAuthByThread();
            return false;
        }
        return true;
    }

    /**
     * 启动线程，联网获取授权
     */
    private void checkAuthByThread() {
        Log.i(TAG, "checkAuthByThread");

        new Thread(new Runnable() {
            @Override
            public void run() {
                int errorCode = mCloudSysHelper.checkAuthByNet();
                
                // check完成后，发送消息到主线程
                Message message = mHandler.obtainMessage(MSG_WHAT_CHECK_AUTH_FINISH, errorCode, errorCode);
                mHandler.sendMessage(message);
            }
        }).start();
    }

    public void pickSuggestionManually(int index, String text) {
    	mComposing.setLength(0);
    	mComposing.append(text);
    	
        if (mCompletionOn && mCompletions != null && index >= 0
                && index < mCompletions.length) {
            CompletionInfo ci = mCompletions[index];
            getCurrentInputConnection().commitCompletion(ci);
            if (mCandidateView != null) {
                mCandidateView.clear();
            }
        } else if (mComposing.length() > 0) {
            // If we were generating candidate suggestions for the current
            // text, we would commit one of them here.  But for this sample,
            // we will just commit the current text.
            commitTyped(getCurrentInputConnection());
        }
    }
    
    /**
     * Helper function to commit any text being composed in to the editor.
     */
    private void commitTyped(InputConnection inputConnection) {
        if (mComposing.length() > 0) {
            inputConnection.commitText(mComposing, mComposing.length());
            mComposing.setLength(0);
            updateCandidates();
        }
    }
    
    /**
     * Update the list of available candidates from the current composing
     * text.  This will need to be filled in by however you are determining
     * candidates.
     */
    private void updateCandidates() {
        if (!mCompletionOn) {
            if (mComposing.length() > 0) {
                ArrayList<String> list = new ArrayList<String>();
                list.add(mComposing.toString());
                setSuggestions(list, true, true);
            } else {
                setSuggestions(null, false, false);
            }
        }
    }
    
    public void setSuggestions(List<String> suggestions, boolean completions,
            boolean typedWordValid) {
//        if (suggestions != null && suggestions.size() > 0) {
//            setCandidatesViewShown(true);
//        } else if (isExtractViewShown()) {
//            setCandidatesViewShown(true);
//        }else{
//        	setCandidatesViewShown(false);
//        }
        
        if (mCandidateView != null) {
            mCandidateView.setSuggestions(suggestions, completions, typedWordValid);
        }
    }
    
    
    private void handleBackspace() {
        final int length = mComposing.length();
        if (length > 0) {
            mComposing.setLength(0);
            getCurrentInputConnection().commitText("", 0);
            updateCandidates();
        } else {
            keyDownUp(KeyEvent.KEYCODE_DEL);
        }
    }
    
    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private void keyDownUp(int keyEventCode) {
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }
    
    //接口OnHwrRecogResultChangedListener的回调方法
	@Override
	public void onResultChanged(HwrRecogResult hwrRecogResult) {
		if(hwrRecogResult == null){
			Log.e(TAG, "hwrRecogResult is null");
			return;
		}
		
		ArrayList<HwrRecogResultItem> hwrRecogResultItems = hwrRecogResult.getResultItemList();
		if(hwrRecogResultItems == null){
			Log.e(TAG, "hwrRecogResultItems is null");			
			return;
		}
		
		ArrayList<String> suggestions = new ArrayList<String>();
		for (int index = 0; index < hwrRecogResultItems.size(); index++) {
			String result = hwrRecogResultItems.get(index).getResult();
			suggestions.add(result);
			
			if(index == 0){
				mComposing.setLength(0);
				mComposing.append(result);
			}
		}
	
		setSuggestions(suggestions, true, true);
	}
	
    @Override
    public void onStartWriteNewWord() {
        String suggestion = mCandidateView.getFirstSuggestions();
        if(!TextUtils.isEmpty(suggestion)){
            pickSuggestionManually(0, suggestion);
        }
    }
	
    private String getWordSeparators() {
        return mWordSeparators;
    }
    
    public boolean isWordSeparator(int code) {
        String separators = getWordSeparators();
        return separators.contains(String.valueOf((char)code));
    }
    
    public void onKeyDelegate(int primaryCode, int[] keyCodes) {
        if (isWordSeparator(primaryCode)) {
            // Handle separator
            if (mComposing.length() > 0) {
                commitTyped(getCurrentInputConnection());
            }
            sendKey(primaryCode);
        } else if (primaryCode == Keyboard.KEYCODE_DELETE) {
            handleBackspace();
        }  else {
            handleCharacter(primaryCode, keyCodes);
        }
    }
    
    /**
     * Helper to send a character to the editor as raw key events.
     */
    private void sendKey(int keyCode) {
        switch (keyCode) {
            case '\n':
                keyDownUp(KeyEvent.KEYCODE_ENTER);
                break;
            default:
                getCurrentInputConnection().commitText(String.valueOf((char) keyCode), 1);
                break;
        }
    }
    
    private void handleCharacter(int primaryCode, int[] keyCodes) {
        getCurrentInputConnection().commitText(String.valueOf((char) primaryCode), 1);
    }
    
    static class MyHandler extends Handler{
    	private WeakReference<Pathfinder> mOuter;
    	
    	public MyHandler(Pathfinder service){
    		mOuter = new WeakReference<Pathfinder>(service);
    	}
    	
		@Override
		public void handleMessage(Message msg) {
			Pathfinder pathfinder = mOuter.get();
			if(pathfinder == null){
				Log.e(TAG, "pathfinder is null.");
				return;
			}
			
			switch (msg.what) {
			case MSG_WHAT_CHECK_AUTH_FINISH:
				int errorCode = msg.arg1;
				if (errorCode  == HciErrorCode.HCI_ERR_NONE) {
					pathfinder.mHciSysInited = pathfinder.checkExpireTimeAndCapkeys();
					Log.d(TAG, "handlerMessage(), mHciSysInited: " + pathfinder.mHciSysInited);
					
					if(pathfinder.mHciSysInited){
						pathfinder.initHwr();
					}
		        }
				break;
			case MSG_WHAT_ASR_RESULT:
			    String asrResult = (String) msg.obj;
			    Log.i(TAG, "handler, asr result = " + asrResult);
			    
			    pathfinder.getCurrentInputConnection().commitText(asrResult, 1);
			    pathfinder.updateCandidates();
			    break;
			default:
				break;
			}
			super.handleMessage(msg);
		}
    }

}
