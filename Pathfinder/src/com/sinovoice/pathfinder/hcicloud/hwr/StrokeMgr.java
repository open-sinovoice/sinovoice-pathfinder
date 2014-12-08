package com.sinovoice.pathfinder.hcicloud.hwr;

public class StrokeMgr {

	private static StrokeMgr sStrokeMgr = null;
	
	public static final int MAX_POINT = 20480;
	private short mPoints[] = null;
	private int mCurIndex;
	
	public boolean isBrush = false;
	
	public static StrokeMgr instance(){
		if ( null == sStrokeMgr )
			sStrokeMgr = new StrokeMgr();
		
		return sStrokeMgr;
	}
	
	protected StrokeMgr(){
		mPoints = new short[MAX_POINT*2];
		mCurIndex = 0;
	}
	
	synchronized public boolean addStroke(short x, short y){
		if (( mCurIndex / 2 ) < ( MAX_POINT - 2 )){
			mPoints[mCurIndex] = x;
			mCurIndex++;
			mPoints[mCurIndex] = y;
			mCurIndex++;
			
			return true;
		}
		
		return false;
	}
	
	/**
	 * 添加代表抬笔标识的笔画数据
	 */
	synchronized public void addPenUpStroke(){
		mPoints[mCurIndex] = -1;
		mCurIndex++;
		mPoints[mCurIndex] = 0;
		mCurIndex++;
	}
	
	/**
	 * 添加代表整个笔迹结束标识的笔画数据
	 */
	synchronized public void addEndStroke(){
		mPoints[mCurIndex] = -1;
		mCurIndex++;
		mPoints[mCurIndex] = -1;
		mCurIndex++;
	}
	
	synchronized public short[] getStroke(){
		return mPoints;
	}
	
	synchronized public int getStrokeCount(){
		return mCurIndex / 2;
	}
	
	synchronized public void resetStroke(){
		mCurIndex = 0;
	}
	
	public synchronized short[] takeOutStroke(){
		int strokesCount = getStrokeCount() * 2;
		short[] strokes = new short[strokesCount];
		for (int index = 0; index < strokesCount; index++) {
			strokes[index] = mPoints[index];
		}
		resetStroke();
		return strokes;
	}
}
