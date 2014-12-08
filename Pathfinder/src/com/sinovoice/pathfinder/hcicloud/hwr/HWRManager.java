package com.sinovoice.pathfinder.hcicloud.hwr;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.sinovoice.hcicloudsdk.api.hwr.HciCloudHwr;
import com.sinovoice.hcicloudsdk.common.HciErrorCode;
import com.sinovoice.hcicloudsdk.common.Session;
import com.sinovoice.hcicloudsdk.common.hwr.HwrConfig;
import com.sinovoice.hcicloudsdk.common.hwr.HwrPenScriptResultItem;
import com.sinovoice.hcicloudsdk.common.hwr.HwrRecogResult;
import com.sinovoice.hcicloudsdk.common.hwr.HwrRecogResultItem;
import com.sinovoice.pathfinder.hcicloud.sys.SysConfig;

public class HWRManager implements OnStrokeViewListener{
	private final String TAG = getClass().getSimpleName();
	
	private static final int MSG_WHAT_HWR_RECOG_RESULT_CHANGED = 0;
	private ArrayList<StrokeData> strokeDatas = new ArrayList<StrokeData>();
    private Object recogLock = new Object();
	
	private static HWRManager instance;
	
	private String currSplitMode;
	private String lastSpliteMode;
	
	private HwrRecogResult outRecogResult;
	
	private StrokeMgr strokeMgr;
	private HwrRecogThread recogThread;
	private OnHwrRecogResultChangedListener onHwrRecogResultChangedListener;
//	private boolean mIsInited;
	private boolean mIsReleaseSuccess;
	
	private Context mContext;
	private Handler handler = new Handler(new Handler.Callback() {
		
		@Override
		public boolean handleMessage(Message msg) {
			if(msg.what == MSG_WHAT_HWR_RECOG_RESULT_CHANGED){
				
				if(onHwrRecogResultChangedListener != null){
					onHwrRecogResultChangedListener.onResultChanged((HwrRecogResult)  msg.obj);
				}				
			}
			return false;
		}
	}) ;
	
	public static HWRManager instance(){
		if(instance == null){
			instance = new HWRManager();
		}
		return instance;
	}
	
	private HWRManager(){
		
	}
	
	public void init(Context context){
		mContext = context;
		
		//初始化hwr
		strokeMgr = StrokeMgr.instance();
		
		Configuration config = mContext.getResources().getConfiguration();
		if(config.orientation == Configuration.ORIENTATION_PORTRAIT
				|| config.orientation == Configuration.ORIENTATION_SQUARE){
			currSplitMode = com.sinovoice.hcicloudsdk.api.hwr.HciCloudHwr.HCI_HWR_SPLIT_MODE_OVERLAP;
		}else if(config.orientation == Configuration.ORIENTATION_LANDSCAPE){
			currSplitMode = com.sinovoice.hcicloudsdk.api.hwr.HciCloudHwr.HCI_HWR_SPLIT_MODE_LINE;
		}else{
			currSplitMode = com.sinovoice.hcicloudsdk.api.hwr.HciCloudHwr.HCI_HWR_SPLIT_MODE_OVERLAP;
		}
		
		lastSpliteMode = currSplitMode;
		
		recogThread =  new HwrRecogThread();
		recogThread.start();
		
//		mIsInited = true;
	}
	
	
	public void setOnHwrRecogResultChangedListener(
			OnHwrRecogResultChangedListener onHwrRecogResultChangedListener) {
		this.onHwrRecogResultChangedListener = onHwrRecogResultChangedListener;
	}
	
	public void release(){
		if(recogThread != null){
			recogThread.setFinish(true);
			synchronized (recogLock) {
				recogLock.notifyAll();
			}
		}
	}
	
	private String getRecogConfig(String splitMode){
		HwrConfig hwrRecogConfig = new HwrConfig();
		hwrRecogConfig.addParam(HwrConfig.PARAM_KEY_CAND_NUM, "10");
		hwrRecogConfig.addParam(HwrConfig.PARAM_KEY_CAP_KEY, SysConfig.CAPKEY_HWR);
		hwrRecogConfig.addParam(HwrConfig.PARAM_KEY_RECOG_RANGE, com.sinovoice.hcicloudsdk.api.hwr.HciCloudHwr.HCI_HWR_RANGE_ALL);
		hwrRecogConfig.addParam(HwrConfig.PARAM_KEY_REALTIME, "yes");
		hwrRecogConfig.addParam(HwrConfig.PARAM_KEY_SPLIT_MODE, splitMode);
		return hwrRecogConfig.getStringConfig();
	}
	
	public void initPenScript(){
		
	}
	
	
	public int penScript(int x, int y){
		return 0;
	}
	
	public List<HwrPenScriptResultItem> getPenScriptRetList(){
		return null;
	}
	private boolean uninitCloudHWR(Session session){
	    int result = HciCloudHwr.hciHwrSessionStop(session);
		Log.v(TAG, "hwr session stop result:" + result);
		
		if(result != HciErrorCode.HCI_ERR_NONE){
			return false;
		}
		
		mIsReleaseSuccess = true;
		return true;
	}
	
	public void setSpliteMode(String spliteMode){
		currSplitMode = spliteMode;
		synchronized (recogLock) {
			strokeDatas.clear();
			
			recogLock.notifyAll();
		}
	}

	public boolean isReleaseSuccess() {
		return mIsReleaseSuccess;
	}

	@Override
	public void onPenUp() {
		//取出笔画数据，并通知线程去识别
		notifyRecog();
	}

	private void notifyRecog() {
		short[] data = strokeMgr.takeOutStroke();
		synchronized (recogLock) {
			strokeDatas.add(new StrokeData(data));			
			recogLock.notifyAll();
		}
		
	}

	@Override
	public void onStrokesOver() {
		notifyRecog();
	}
	
	public class HwrRecogThread extends Thread{
		
		private boolean isFinish;

		public void setFinish(boolean isFinish) {
			this.isFinish = isFinish;
		}

		@Override
		public void run() {
			Session session = new Session();
			int result = HciCloudHwr.hciHwrSessionStart(getRecogConfig(currSplitMode), session);
			Log.v(TAG, "sessionStartResult = " + result);
			
			while(true){
				if(isFinish){
				    uninitCloudHWR(session);
//					mIsInited = false;
					break;
				}
				
				synchronized (recogLock) {
					int size = strokeDatas.size();
					if(size > 0){
						short[] data = strokeDatas.remove(0).getData();
						
						if(!lastSpliteMode.equals(currSplitMode)){
							Log.i(TAG, "叠写和行写模式发生改变，stop session并重启session。currSpliteMode = " + currSplitMode);
							
							lastSpliteMode = currSplitMode;
							
							int errorCode = HciCloudHwr.hciHwrSessionStop(session);
							Log.v(TAG, "hwr session stop result:" + errorCode);
							
							errorCode = HciCloudHwr.hciHwrSessionStart(getRecogConfig(currSplitMode), session);
							Log.v(TAG, "sessionStartResult = " + errorCode);		
						}
						
						//开始识别
						try {
							recog(session, data, getRecogConfig(currSplitMode));
						} catch (Exception e) {
							e.printStackTrace();
						}
					}else{
						try {
							recogLock.wait();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			}
			
		}
		
		private void recog(Session session, short[] recogPoint, String strConfig) throws Exception{
			//遇到-1，-1就结束识别
			if(recogPoint.length <= 4 && recogPoint[recogPoint.length - 2] == -1 && recogPoint[recogPoint.length - 1] == -1){
				Log.i(TAG, "笔迹结束，stop Session，并重新启动Session。");
				
				int result = HciCloudHwr.hciHwrSessionStop(session);
				Log.v(TAG, "hwr session stop result:" + result);
				
				result = HciCloudHwr.hciHwrSessionStart(strConfig, session);
				Log.v(TAG, "sessionStartResult = " + result);
				return;
			}
			
			outRecogResult = new HwrRecogResult();
			int ret = HciCloudHwr.hciHwrRecog(session, recogPoint, strConfig, outRecogResult);
			Log.v(TAG, "recogResult = " + ret);
			
			ArrayList<HwrRecogResultItem> recogResultList = outRecogResult.getResultItemList();
			if(recogResultList != null){
				for (HwrRecogResultItem hwrRecogResultItem : recogResultList) {
					Log.v(TAG, "" + hwrRecogResultItem.getResult());
				}
			}
			
			Message message = Message.obtain();
			message.what = MSG_WHAT_HWR_RECOG_RESULT_CHANGED;
			message.obj = outRecogResult;
			handler.sendMessage(message);
		}
		
	}
}
