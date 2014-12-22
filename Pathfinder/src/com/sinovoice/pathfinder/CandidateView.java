/*
 * Copyright (C) 2008-2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sinovoice.pathfinder;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.sinovoice.hcicloudsdk.api.asr.HciCloudAsr;
import com.sinovoice.hcicloudui.recorder.JTAsrRecogParams;
import com.sinovoice.hcicloudui.recorder.JTAsrRecorderDialog;
import com.sinovoice.hcicloudui.recorder.JTAsrRecorderDialog.JTAsrListener;
import com.sinovoice.pathfinder.hcicloud.sys.SysConfig;

@SuppressLint("WrongCall")
public class CandidateView extends View {
    private static final String TAG = CandidateView.class.getSimpleName();

    private static final int OUT_OF_BOUNDS = -1;

    private Handler mHandler;
    private Pathfinder mService;
    private List<String> mSuggestions;
    private int mSelectedIndex;
    private int mTouchX = OUT_OF_BOUNDS;
    private Drawable mSelectionHighlight;
    private boolean mTypedWordValid;

    private Rect mBgPadding;

    private static final int MAX_SUGGESTIONS = 32;
    private static final int SCROLL_PIXELS = 20;

    private int[] mWordWidth = new int[MAX_SUGGESTIONS];
    private int[] mWordX = new int[MAX_SUGGESTIONS];

    private static final int X_GAP = 20;

    private static final List<String> EMPTY_LIST = new ArrayList<String>();

    private int mColorNormal;
    private int mColorRecommended;
    private int mColorOther;
    private int mVerticalPadding;
    private Paint mPaint;
    private boolean mScrolled;
    private int mTargetScrollX;

    private int mTotalWidth;

    /**
     * 绘制mic
     */
    private Rect mVoiceRect;
    private Drawable mVoiceDrawable;
    
    /**
     * 语音识别dialog
     */
    private JTAsrRecorderDialog mRecorderDialog;
    private String mAsrResult;
    
    private JTAsrListener asrListener = new JTAsrListener() {
        @Override
        public void onResult(ArrayList<String> resultList) {
            if (resultList != null && resultList.size() > 0) {
                mAsrResult = resultList.get(0);
            }
        }

        @Override
        public void onError(int errorCode, String details) {
            Log.e(TAG, "asrDialogListener error, code = " + errorCode
                    + ", details = " + details);
        }
    };

    /**
     * 标识候选栏当前状态
     */
    private CandidateState mCandidateState;

    private GestureDetector mGestureDetector;

    /**
     * Construct a CandidateView for showing suggested words for completion.
     * 
     * @param context
     * @param attrs
     */
    public CandidateView(Context context) {
        super(context);
        mService = (Pathfinder) context;
        mSelectionHighlight = context.getResources().getDrawable(
                android.R.drawable.list_selector_background);
        mSelectionHighlight.setState(new int[] { android.R.attr.state_enabled,
                android.R.attr.state_focused,
                android.R.attr.state_window_focused,
                android.R.attr.state_pressed });

        Resources r = context.getResources();

        setBackgroundColor(r.getColor(R.color.candidate_background));

        mColorNormal = r.getColor(R.color.candidate_normal);
        mColorRecommended = r.getColor(R.color.candidate_recommended);
        mColorOther = r.getColor(R.color.candidate_other);
        mVerticalPadding = r
                .getDimensionPixelSize(R.dimen.candidate_vertical_padding);

        mPaint = new Paint();
        mPaint.setColor(mColorNormal);
        mPaint.setAntiAlias(true);
        mPaint.setTextSize(r
                .getDimensionPixelSize(R.dimen.candidate_font_height));
        mPaint.setStrokeWidth(0);

        mGestureDetector = new GestureDetector(
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onScroll(MotionEvent e1, MotionEvent e2,
                            float distanceX, float distanceY) {
                        mScrolled = true;
                        int sx = getScrollX();
                        sx += distanceX;
                        if (sx < 0) {
                            sx = 0;
                        }
                        if (sx + getWidth() > mTotalWidth) {
                            sx -= distanceX;
                        }
                        mTargetScrollX = sx;
                        scrollTo(sx, getScrollY());
                        invalidate();
                        return true;
                    }
                });
        setHorizontalFadingEdgeEnabled(true);
        setWillNotDraw(false);
        setHorizontalScrollBarEnabled(false);
        setVerticalScrollBarEnabled(false);

        mVoiceDrawable = context.getResources().getDrawable(
                R.drawable.bar_microphone);
        initAsrDialog();
        
        setCandidateState(CandidateState.CAN_STATE_IDLE);
    }

    /**
     * 初始化录音dialog
     */
    private void initAsrDialog() {
        mRecorderDialog = new JTAsrRecorderDialog(mService, asrListener);
        Window window = mRecorderDialog.getWindow();
        window.setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
//        window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        mRecorderDialog.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if(TextUtils.isEmpty(mAsrResult)){
                    return;
                }
                
                Message msg = mHandler.obtainMessage(Pathfinder.MSG_WHAT_ASR_RESULT, mAsrResult);
                mHandler.sendMessage(msg);
                mAsrResult = "";
            }
        });

        JTAsrRecogParams asrRecogParams = new JTAsrRecogParams();
        asrRecogParams.setCapKey(SysConfig.CAPKEY_ASR);
        asrRecogParams
                .setAudioFormat(HciCloudAsr.HCI_ASR_AUDIO_FORMAT_PCM_16K16BIT);
        asrRecogParams.setMaxSeconds("60");
        asrRecogParams.setAddPunc("yes");

        // 获取手机核心数,根据手机核心数定义压缩方式
        int cpuCoreNum = getNumCores();
        if (cpuCoreNum > 1) {
            asrRecogParams.setEncode(HciCloudAsr.HCI_ASR_ENCODE_SPEEX);
        } else {
            asrRecogParams.setEncode(HciCloudAsr.HCI_ASR_ENCODE_ALAW);
        }
        
        mRecorderDialog.setParams(asrRecogParams);
    }

    private int getNumCores() {
        class CpuFilter implements FileFilter {
            @Override
            public boolean accept(File pathname) {
                if (Pattern.matches("cpu[0-9]", pathname.getName())) {
                    return true;
                }
                return false;
            }
        }

        try {
            // 获取手机CPU信息
            File dir = new File("/sys/devices/system/cpu/");
            File[] files = dir.listFiles(new CpuFilter());

            return files.length;
        } catch (Exception e) {

            e.printStackTrace();
            return 1;
        }
    }

    /**
     * A connection back to the service to communicate with the text field
     * 
     * @param listener
     */
    public void setService(Pathfinder listener) {
        mService = listener;
    }
    
    public void setHandler(Handler handler){
        mHandler = handler;
    }

    @Override
    public int computeHorizontalScrollRange() {
        return mTotalWidth;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int measuredWidth = resolveSize(50, widthMeasureSpec);
        Log.i(TAG, "onMeasure");
        // Get the desired height of the icon menu view (last row of items does
        // not have a divider below)
        Rect padding = new Rect();
        mSelectionHighlight.getPadding(padding);
        final int desiredHeight = ((int) mPaint.getTextSize())
                + mVerticalPadding + padding.top + padding.bottom + 20;

        // Maximum possible width and desired height
        int measuredHeight = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(measuredWidth, measuredHeight);

        if (mVoiceRect == null) {
            mVoiceRect = new Rect();
            int halfHeight = measuredHeight / 2;
            mVoiceRect.set(measuredWidth / 2 - halfHeight, 5, measuredWidth / 2 + halfHeight,
                    measuredHeight - 5);
        }
    }

    /**
     * If the canvas is null, then only touch calculations are performed to pick
     * the target candidate.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        Log.i(TAG, "onDraw");
        if (canvas != null) {
            super.onDraw(canvas);
        }
        mTotalWidth = 0;

        if (mBgPadding == null) {
            mBgPadding = new Rect(0, 0, 0, 0);
            if (getBackground() != null) {
                getBackground().getPadding(mBgPadding);
            }
        }

        switch (mCandidateState) {
        case CAN_STATE_CANDIDATE:
        	if (mSuggestions != null) {
        		drawSuggestions(canvas);
        		
        		if (mTargetScrollX != getScrollX()) {
        			scrollToTarget();
        		}
        	}
            break;
        case CAN_STATE_IDLE:
            drawAsrRecorderMic(canvas);
            break;
        default:
            break;
        }
    }

    private void drawAsrRecorderMic(Canvas canvas) {
        if (canvas == null) {
            return;
        }

        mVoiceDrawable.setBounds(mVoiceRect);
        mVoiceDrawable.draw(canvas);
    }

    private void drawSuggestions(Canvas canvas) {
        if (canvas == null) {
            return;
        }

        int x = 0;
        final int count = mSuggestions.size();
        final int height = getHeight();
        final Rect bgPadding = mBgPadding;
        final Paint paint = mPaint;
        final int touchX = mTouchX;
        final int scrollX = getScrollX();
        final boolean scrolled = mScrolled;
        final boolean typedWordValid = mTypedWordValid;
        final int y = (int) (((height - mPaint.getTextSize()) / 2) - mPaint
                .ascent());

        for (int i = 0; i < count; i++) {
            String suggestion = mSuggestions.get(i);
            float textWidth = paint.measureText(suggestion);
            final int wordWidth = (int) textWidth + X_GAP * 2;

            mWordX[i] = x;
            mWordWidth[i] = wordWidth;
            paint.setColor(mColorNormal);
            if (touchX + scrollX >= x && touchX + scrollX < x + wordWidth
                    && !scrolled) {
                if (canvas != null) {
                    canvas.translate(x, 0);
                    mSelectionHighlight.setBounds(0, bgPadding.top, wordWidth,
                            height);
                    mSelectionHighlight.draw(canvas);
                    canvas.translate(-x, 0);
                }
                mSelectedIndex = i;
            }

            if (canvas != null) {
                if ((i == 1 && !typedWordValid) || (i == 0 && typedWordValid)) {
                    paint.setFakeBoldText(true);
                    paint.setColor(mColorRecommended);
                } else if (i != 0) {
                    paint.setColor(mColorOther);
                }
                canvas.drawText(suggestion, x + X_GAP, y, paint);
                paint.setColor(mColorOther);
                canvas.drawLine(x + wordWidth + 0.5f, bgPadding.top, x
                        + wordWidth + 0.5f, height + 1, paint);
                paint.setFakeBoldText(false);
            }
            x += wordWidth;
        }
        mTotalWidth = x;
    }

    private void scrollToTarget() {
        int sx = getScrollX();
        if (mTargetScrollX > sx) {
            sx += SCROLL_PIXELS;
            if (sx >= mTargetScrollX) {
                sx = mTargetScrollX;
                requestLayout();
            }
        } else {
            sx -= SCROLL_PIXELS;
            if (sx <= mTargetScrollX) {
                sx = mTargetScrollX;
                requestLayout();
            }
        }
        scrollTo(sx, getScrollY());
        invalidate();
    }

    public void setSuggestions(List<String> suggestions, boolean completions,
            boolean typedWordValid) {
        clear();
        if (suggestions != null) {
            mSuggestions = new ArrayList<String>(suggestions);

            setCandidateState(CandidateState.CAN_STATE_CANDIDATE);
        }
        mTypedWordValid = typedWordValid;
        scrollTo(0, 0);
        mTargetScrollX = 0;
        // Compute the total width
        onDraw(null);
        invalidate();
        requestLayout();
    }

    public void clear() {
        mSuggestions = EMPTY_LIST;
        mTouchX = OUT_OF_BOUNDS;
        mSelectedIndex = -1;

        setCandidateState(CandidateState.CAN_STATE_IDLE);
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent me) {
        boolean touch = true;

        switch (mCandidateState) {
        case CAN_STATE_CANDIDATE:
        	if (mGestureDetector.onTouchEvent(me)) {
        		return true;
        	}
        	
            // 存在候选词
            touch = touchSuggestions(me);
            break;
        case CAN_STATE_IDLE:
            // 点击mic
            touchAsrRecorderMic(me);
            break;
        default:
            break;
        }

        return touch;
    }

    private boolean touchAsrRecorderMic(MotionEvent me) {
        int action = me.getAction();
        int x = (int) me.getX();
        int y = (int) me.getY();
        // mTouchX = x;

        switch (action) {
        case MotionEvent.ACTION_DOWN:
            mScrolled = false;
            invalidate();
            break;
        case MotionEvent.ACTION_UP:
            if (!mScrolled && mVoiceRect.contains(x, y)) {
                Log.i(TAG, "press mic");
                mRecorderDialog.start();
            }
//            removeHighlight();
//            requestLayout();
            break;
        }

        return true;
    }

    private boolean touchSuggestions(MotionEvent me) {
        int action = me.getAction();
        int x = (int) me.getX();
        int y = (int) me.getY();
        mTouchX = x;

        switch (action) {
        case MotionEvent.ACTION_DOWN:
            mScrolled = false;
            invalidate();
            break;
        case MotionEvent.ACTION_MOVE:
            if (y <= 0) {
                // Fling up!?
                if (mSelectedIndex >= 0) {
                    mService.pickSuggestionManually(mSelectedIndex,
                            mSuggestions.get(mSelectedIndex));
                    mSelectedIndex = -1;
                }
            }
            invalidate();
            break;
        case MotionEvent.ACTION_UP:
            if (!mScrolled) {
                if (mSelectedIndex >= 0) {
                    mService.pickSuggestionManually(mSelectedIndex,
                            mSuggestions.get(mSelectedIndex));
                }
            }
            mSelectedIndex = -1;
            removeHighlight();
            requestLayout();
            break;
        }
        return true;
    }

    /**
     * For flick through from keyboard, call this method with the x coordinate
     * of the flick gesture.
     * 
     * @param x
     */
    public void takeSuggestionAt(float x) {
        mTouchX = (int) x;
        // To detect candidate
        onDraw(null);
        if (mSelectedIndex >= 0) {
            mService.pickSuggestionManually(mSelectedIndex,
                    mSuggestions.get(mSelectedIndex));
        }
        invalidate();
    }

    private void removeHighlight() {
        mTouchX = OUT_OF_BOUNDS;
        invalidate();
    }

    private void setCandidateState(CandidateState state) {
        mCandidateState = state;
    }

    public enum CandidateState {

        /**
         * 正常状态，无候选
         */
        CAN_STATE_IDLE,

        /**
         * 存在候选词状态
         */
        CAN_STATE_CANDIDATE
    }
}
