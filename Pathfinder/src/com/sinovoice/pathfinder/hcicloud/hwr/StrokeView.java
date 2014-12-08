package com.sinovoice.pathfinder.hcicloud.hwr;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.TextView;

import com.sinovoice.hcicloudsdk.common.HciErrorCode;
import com.sinovoice.hcicloudsdk.common.hwr.HwrPenScriptResultItem;

public class StrokeView extends TextView {
	private static final String TAG = StrokeView.class.getSimpleName();
	private final int[] mFadeColor = { 0xffffffff, 0xeeffffff, 0xccffffff,
            0xbbffffff, 0x99ffffff, 0x66ffffff, 0x55ffffff, 0x44ffffff,
            0x39ffffff, 0x33ffffff, 0x22ffffff, 0x17ffffff, 0x11ffffff,
            0x09ffffff, 0x05ffffff, 0x00ffffff };
	
	// 抬笔超时FLAG
	private static final int TIMEUP_MSG = 1;
	private static final int PRE_RECOG_TIMEUP_MSG = 2;

	private Rect mRect = new Rect();
	private Rect mRectInDrawMethod = new Rect();
	private Paint mPaint;
	private Paint mBorderPaint;

	private int mCurX;
	private int mCurY;

	private int mViewWidth;
	private int mViewHeight;

	List<PathInfo> mPathInfo = new ArrayList<PathInfo>();

	private int mPrevX;
	private int mPrevY;
	private Path mPath = new Path();
	private RectF mRectF = new RectF();

	private int mScriptWidth = 9;
	private int mScriptColor = 0xff01A33E;
	
	private int mUpTime = 1000;
	private int mPenUpTimeout = 100;

	private Paint mBrushPaint;

	private Bitmap mPenScriptBitmap;
	private OnStrokeViewListener onStrokeViewListener;
	
	private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case PRE_RECOG_TIMEUP_MSG:
                if(onStrokeViewListener != null){
                    onStrokeViewListener.onPenUp();
                }
                break;
            case TIMEUP_MSG:
                if (StrokeMgr.instance().isBrush) {
                    HWRManager.instance().initPenScript();
                }
                StrokeMgr.instance().addEndStroke();
                if(onStrokeViewListener != null){
                    onStrokeViewListener.onStrokesOver();
                }
                
                clear();
                StrokeMgr.instance().resetStroke();
                break;
            default:
                super.handleMessage(msg);
            }
        }
    };

	public void setOnStrokeViewListener(OnStrokeViewListener onStrokeViewListener) {
		this.onStrokeViewListener = onStrokeViewListener;
	}

	public StrokeView(Context context) {
		super(context);
		
		initView();
	}

	public StrokeView(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		initView();
	}

	private void initView() {
		mPath = new Path();
		mPaint = new Paint();
		mPaint.setAntiAlias(true);
		mPaint.setDither(true);
		mPaint.setStyle(Paint.Style.STROKE);
		mPaint.setStrokeCap(Paint.Cap.ROUND);
		mPaint.setStrokeJoin(Paint.Join.ROUND);
		mPaint.setStrokeWidth(mScriptWidth);
		mPaint.setColor(mScriptColor);

		mBorderPaint = new Paint();
		mBorderPaint.setAntiAlias(true);
		mBorderPaint.setStyle(Paint.Style.STROKE);
		mBorderPaint.setStrokeCap(Paint.Cap.ROUND);
		mBorderPaint.setStrokeJoin(Paint.Join.ROUND);
		mBorderPaint.setStrokeWidth(5);
		mBorderPaint.setColor(0xFF000000);

		mBrushPaint = new Paint();
		mBrushPaint.setStyle(Paint.Style.FILL);
		mBrushPaint.setStrokeCap(Paint.Cap.ROUND);
		mBrushPaint.setStrokeJoin(Paint.Join.ROUND);

	}

	/**
	 * 清除所有内容
	 */
	public void clear() {
	    Log.d(TAG, "clear()");
	    
		mPath.reset();
		mPathInfo.clear();
		mRect.setEmpty();

		if (mPenScriptBitmap != null) {
			mPenScriptBitmap.eraseColor(Color.TRANSPARENT);
		}

		invalidate();
	}

	@Override
	protected void onSizeChanged(final int w, final int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);

		mViewWidth = getWidth();
		mViewHeight = getHeight();
		
		if (mPenScriptBitmap == null) {
			// 笔形图片
			mPenScriptBitmap = Bitmap.createBitmap(mViewWidth, mViewHeight,
					Bitmap.Config.ARGB_8888);
			mPenScriptBitmap.eraseColor(Color.TRANSPARENT);
		}
	}

	/**
	 * @see android.view.View#measure(int, int)
	 */
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int width = measureWidth(widthMeasureSpec);
		int height = measureHeight(heightMeasureSpec);
		setMeasuredDimension(width, height);
	}

	/**
	 * Determines the width of this view
	 * 
	 * @param measureSpec
	 *            A measureSpec packed into an int
	 * @return The width of the view, honoring constraints from measureSpec
	 */
	private int measureWidth(int measureSpec) {
		int result = 0;
		int specSize = MeasureSpec.getSize(measureSpec);
		result = specSize;

		return result;
	}

	/**
	 * Determines the height of this view
	 * 
	 * @param measureSpec
	 *            A measureSpec packed into an int
	 * @return The height of the view, honoring constraints from measureSpec
	 */
	private int measureHeight(int measureSpec) {
		int result = 0;
		int specSize = MeasureSpec.getSize(measureSpec);
		result = specSize;

		return result;
	}

	/**
	 * Render the text
	 * 
	 * @see android.view.View#onDraw(android.graphics.Canvas)
	 */
    @Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		// 绘制边框
		drawBorder(canvas);

//		Rect r = canvas.getClipBounds();
		canvas.getClipBounds(mRectInDrawMethod);

		if (StrokeMgr.instance().isBrush) {
			canvas.drawBitmap(mPenScriptBitmap, 0, 0, mBrushPaint);
		} else {
			for (int i = 0; i < mPathInfo.size(); i++) {
				PathInfo pathInfo = mPathInfo.get(i);

				if (Rect.intersects(mRectInDrawMethod, pathInfo.rect)){
					mPaint.setColor(pathInfo.color);
				}
				
				canvas.drawPath(pathInfo.path, mPaint);
			}
			mPaint.setColor(mScriptColor);
			canvas.drawPath(mPath, mPaint);
		}
	}

	/**
	 * 绘制边框
	 * 
	 * @param canvas
	 */
	private void drawBorder(Canvas canvas) {
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		int action = event.getAction();
		if (action == MotionEvent.ACTION_DOWN) {
			stopUpTimer();

			mCurX = (int) event.getX();
			mCurY = (int) event.getY();
			mPrevX = mCurX;
			mPrevY = mCurY;
			StrokeMgr.instance().addStroke((short) mCurX, (short) mCurY);

			if (StrokeMgr.instance().isBrush) {
				drawPenScript(mCurX - 1, mCurY);
				drawPenScript(mCurX - 1, mCurY - 1);
				drawPenScript(mCurX, mCurY - 1);
				drawPenScript(mCurX, mCurY);
			}

			mPath = new Path();
			mPath.moveTo(mCurX, mCurY);

			invalidate();

			return true;
		}

		int N = event.getHistorySize();
		int x = 0;
		int y = 0;
		for (int i = 0; i < N; i++) {
			x = (int) event.getHistoricalX(i);
			y = (int) event.getHistoricalY(i);
			drawPoint(mCurX, mCurY, x, y);
			mCurX = x;
			mCurY = y;
			StrokeMgr.instance().addStroke((short) mCurX, (short) mCurY);
		}

		x = (int) event.getX();
		y = (int) event.getY();
		drawPoint(mCurX, mCurY, x, y);
		mCurX = x;
		mCurY = y;
		StrokeMgr.instance().addStroke((short) mCurX, (short) mCurY);

		if (action == MotionEvent.ACTION_MOVE) {
			mPath.computeBounds(mRectF, true);
			mRect.set(((int) mRectF.left - mScriptWidth),
					((int) mRectF.top - mScriptWidth),
					((int) mRectF.right + mScriptWidth),
					((int) mRectF.bottom + mScriptWidth));
//			Log.v(TAG, "action move, " + mRect.left + ", " + mRect.right + ", " + mRect.top + ", " + mRect.bottom);
			
			if (StrokeMgr.instance().isBrush) {
				drawPenScript(mCurX, mCurY);
			}

		} else if (action == MotionEvent.ACTION_UP) {
			StrokeMgr.instance().addStroke((short) -1, (short) 0);

			mPath.computeBounds(mRectF, true);
			mRect.set(((int) mRectF.left - mScriptWidth),
					((int) mRectF.top - mScriptWidth),
					((int) mRectF.right + mScriptWidth),
					((int) mRectF.bottom + mScriptWidth));

//			Log.v(TAG, "action up, " + mRect.left + ", " + mRect.right + ", " + mRect.top + ", " + mRect.bottom);
			
			PathInfo pathInfo = new PathInfo(mPath, mRect);
			mPathInfo.add(pathInfo);

			fadePoints();

			mCurX = -1;
			mCurY = 0;

			if (StrokeMgr.instance().isBrush) {
				drawPenScript(mCurX, mCurY);
			}

			startUpTimer();
		}
		
		invalidate(mRect);

		return true;
	}

	private void fadePoints() {
		int count = mPathInfo.size();
		int start = count - 1;
		int end = count - 16;
		end = end < 0 ? 0 : end;

		PathInfo pathInfo;
		int index = 0;
		for (int i = start; i >= end; i--, index++) {
			pathInfo = mPathInfo.get(i);

			 pathInfo.color = ( pathInfo.color | 0xff000000 ) &
			 mFadeColor[index];

			mRect.union(pathInfo.rect);
		}
	}

	private void drawPoint(int x1, int y1, int x2, int y2) {
		mPath.moveTo(mPrevX, mPrevY);
		mPath.quadTo(x1, y1, (x1 + x2) / 2, (y1 + y2) / 2);

		mPrevX = (x1 + x2) / 2;
		mPrevY = (y1 + y2) / 2;
	}

	public void startUpTimer() {
		mHandler.removeMessages(PRE_RECOG_TIMEUP_MSG);
		mHandler.sendMessageDelayed(mHandler.obtainMessage(PRE_RECOG_TIMEUP_MSG), mPenUpTimeout);
		
		mHandler.removeMessages(TIMEUP_MSG);
		mHandler.sendMessageDelayed(mHandler.obtainMessage(TIMEUP_MSG), mUpTime);
	}

	public void stopUpTimer() {
		setText("");
		
		mHandler.removeMessages(TIMEUP_MSG);
		mHandler.removeMessages(PRE_RECOG_TIMEUP_MSG);
	}

	/**
	 * 绘制笔式的效果
	 * 
	 * @param x
	 * @param y
	 */
	private void drawPenScript(int x, int y) {
		int penScriptResult = HWRManager.instance().penScript(x, y);

		Canvas canvas = new Canvas(this.mPenScriptBitmap);
		if (penScriptResult == HciErrorCode.HCI_ERR_NONE) {
			List<HwrPenScriptResultItem> items = HWRManager
					.instance().getPenScriptRetList();
			for (int k = 0; k < items.size(); k++) {

				HwrPenScriptResultItem item = items.get(k);
				short[] pageImg = item.getPageImg();
				long colorL = item.getPenColor();

				// 将unsigned 的值变为 颜色值
				colorL = colorL & 0xffffffL;
				colorL = colorL | 0xff000000L;
				mBrushPaint.setColor((int) colorL);

				for (int h = 0; h < item.getHeight(); h++) {
					for (int w = 0; w < item.getWidth(); w++) {
						int pos = h * item.getWidth() + w;
						if (pageImg[pos] == 0) {
							canvas.drawPoint(w + item.getX(), h + item.getY(),
									mBrushPaint);
						}
					}
				}
			}
		} else {
			StrokeMgr.instance().isBrush = false;
		}
	}

	public void reloadStrokeSetting() {
		mPaint.setColor(mScriptColor);
		mPaint.setStrokeWidth(mScriptWidth);
	}

	class PathInfo {
		Path path;
		Rect rect;
		int color;

		public PathInfo(Path pth, Rect rc) {
			path = pth;
			rect = new Rect(rc);
			color = mScriptColor;
		}

		public PathInfo(Path pth, int left, int top, int right, int bottom) {
			path = pth;
			rect = new Rect(left, top, right, bottom);
		}
	}

	public void setScriptWidth(int scriptWidth) {
		mScriptWidth = scriptWidth;
		mPaint.setStrokeWidth(mScriptWidth);
	}

	public void setStrokeColor(int color) {
		mScriptColor = color;
		mPaint.setColor(color);
	}
}
