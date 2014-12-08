package com.sinovoice.pathfinder;

import android.content.Context;
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.ViewFlipper;

import com.sinovoice.pathfinder.hcicloud.hwr.HWRManager;
import com.sinovoice.pathfinder.hcicloud.hwr.StrokeView;

public class SVInputView extends ViewFlipper implements OnKeyboardActionListener{
	private static final String TAG = SVInputView.class.getSimpleName();
	
	private Pathfinder mPathfinder;
	private Context mContext;
	private LayoutInflater mInflater;
	private ViewGroup.LayoutParams mLayoutParams;
	
	private RelativeLayout mKeyboardHwrLayout;
	private SVKeyboardHwrView mKeyBoardHwrView;
	private SVKeyboard mKeyBoardHwr;
	
	private StrokeView mHwrStrokeView;
	
	public SVInputView(Context context) {
		super(context);
	}

	public SVInputView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public void init(Pathfinder pathfinder){
		mPathfinder = pathfinder;
		mContext = getContext();
		mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mKeyboardHwrLayout = (RelativeLayout) mInflater.inflate(R.layout.keyboard_hwr_half_screen, null);
		
		mKeyBoardHwrView = (SVKeyboardHwrView) mKeyboardHwrLayout.findViewById(R.id.hwr_keyboard_view);
		mKeyBoardHwr = new SVKeyboard(mContext, R.xml.hwr_ctrl);
		mKeyBoardHwrView.setKeyboard(mKeyBoardHwr);
		mKeyBoardHwrView.setPreviewEnabled(false);
		mKeyBoardHwrView.setOnKeyboardActionListener(this);
		
		mHwrStrokeView = (StrokeView) mKeyboardHwrLayout.findViewById(R.id.hwr_stoke_view);
		mHwrStrokeView.setOnStrokeViewListener(HWRManager.instance());
		
		int height = mContext.getResources().getDimensionPixelSize(R.dimen.input_view_height);
		mLayoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
		addView(mKeyboardHwrLayout, mLayoutParams);
	}

	//以下为接口OnKeyboardActionListener的回调方法
	@Override
	public void onPress(int primaryCode) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onRelease(int primaryCode) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onKey(int primaryCode, int[] keyCodes) {
		mPathfinder.onKeyDelegate(primaryCode, keyCodes);
	}

	@Override
	public void onText(CharSequence text) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void swipeLeft() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void swipeRight() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void swipeDown() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void swipeUp() {
		// TODO Auto-generated method stub
		
	}
	
}
