package com.sinovoice.pathfinder.hcicloud.sys;

import java.io.File;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.sinovoice.hcicloudsdk.api.HciCloudSys;
import com.sinovoice.hcicloudsdk.common.AuthExpireTime;
import com.sinovoice.hcicloudsdk.common.CapabilityItem;
import com.sinovoice.hcicloudsdk.common.HciErrorCode;
import com.sinovoice.hcicloudsdk.common.InitParam;

public class HciCloudSysHelper {
    private static final String TAG = HciCloudSysHelper.class.getSimpleName();
    
    public static final int ERRORCODE_NONE = 0;
    public static final int ERRORCODE_AUTH_FILE_INVALID = 1;
    public static final int ERRORCODE_AUTH_FILE_WILL_EXPIRED = 2;
    public static final int ERRORCODE_AUTH_FILE_HAS_EXPIRED = 3;
    
    private static HciCloudSysHelper mInstance;

    private HciCloudSysHelper() {
    	
    }

    public static HciCloudSysHelper getInstance() {
        if (mInstance == null) {
            mInstance = new HciCloudSysHelper();
        }
        return mInstance;
    }

    /**
     * HciCloud系统初始化
     * @param context
     * @return
     * 
     */
    public int init(Context context) {
        String initConfig = getInitConfig(context);
        Log.i(TAG, "initConfig: " + initConfig);

        // 初始化
        int initErrorCode = HciCloudSys.hciInit(initConfig, context);
        if (initErrorCode != HciErrorCode.HCI_ERR_NONE) {
            Log.e(TAG, "hciInit error. initErrorCode: " + initErrorCode);
        } else {
            Log.i(TAG, "hciInit success.");
        }
        
        return initErrorCode;
    }

    /**
     * 检查授权过期时间
     * 
     * @return
     */
    public int checkExpireTime() {
    	int result = ERRORCODE_NONE;
    	
        AuthExpireTime expireTime = new AuthExpireTime();
        int errorCode = HciCloudSys.hciGetAuthExpireTime(expireTime);
        Log.d(TAG, "hciGetAuthExpireTime(), errorCode: " + errorCode);
        
        if (errorCode == HciErrorCode.HCI_ERR_SYS_AUTHFILE_INVALID) {
            // 授权文件不存在或非法
        	result = ERRORCODE_AUTH_FILE_INVALID;
        } else if (errorCode == HciErrorCode.HCI_ERR_NONE) {
            // 读取成功并判断过期时间
            long expireTimeValue = expireTime.getExpireTime();
            long currTime = System.currentTimeMillis();

            // 距离过期时间ms数，此处为1天
            final long TIME_DIFFERENCE_MAX = 1 * 24 * 3600 * 1000L;
            long timeDifference = expireTimeValue * 1000 - currTime;

            if(timeDifference < 0){
            	result = ERRORCODE_AUTH_FILE_HAS_EXPIRED;
            }else if (timeDifference < TIME_DIFFERENCE_MAX) {
                // 时间间隔小于设定的值
            	result = ERRORCODE_AUTH_FILE_WILL_EXPIRED;
            }else{
            	Log.v(TAG, "authfile expireTime is valid.");
            }
        } else {
            //
        	Log.e(TAG, "未处理的特殊情况？");
        }
        
        return result;
    }
    

    /**
     * 联网获取授权
     * 
     * @return 
     */
    public int checkAuthByNet() {
        int errorCode = HciCloudSys.hciCheckAuth();
        Log.v(TAG, "hciCheckAuth(), errorCode: " + errorCode);
        
        if(errorCode == HciErrorCode.HCI_ERR_NONE){
        	Log.v(TAG, "hciCheckAuth success.");
        }else{
        	Log.e(TAG, "hciCheckAuth fail.");
        }
        return errorCode;
    }

    /**
     * 检查全部capkey是否可用
     * 
     * @return
     */
    public int checkCapkeysEnable() {
    	int errorCode = HciErrorCode.HCI_ERR_NONE;
        for (String capKey : SysConfig.ALL_CAPKEY_ARRAY) {
            CapabilityItem item = new CapabilityItem();
            errorCode = HciCloudSys.hciGetCapability(capKey, item);
            item = null;
            if (errorCode != HciErrorCode.HCI_ERR_NONE) {
                Log.e(TAG, "hciGetCapability() fail, code: " + errorCode + ", capKey: " + capKey);
                break;
            }
        }
        return errorCode;
    }

    /**
     * 系统反初始化
     */
    public void release() {
        int errorCode = HciCloudSys.hciRelease();
        Log.i(TAG, "hciRelease(), errorCode: " + errorCode);
    }

    /**
     * 加载初始化信息
     * 
     * @param context
     *            上下文语境
     * @return 系统初始化参数
     */
    private String getInitConfig(Context context) {
        String authDirPath = context.getFilesDir().getAbsolutePath();

        // 前置条件：无
        InitParam initparam = new InitParam();

        // 授权文件所在路径，此项必填
        initparam.addParam(InitParam.PARAM_KEY_AUTH_PATH, authDirPath);

        // 是否自动访问云授权,详见 获取授权/更新授权文件处注释
        initparam.addParam(InitParam.PARAM_KEY_AUTO_CLOUD_AUTH, "no");

        // 灵云云服务的接口地址，此项必填
        initparam.addParam(InitParam.PARAM_KEY_CLOUD_URL, SysConfig.CLOUDURL);

        // 开发者Key，此项必填，由捷通华声提供
        initparam.addParam(InitParam.PARAM_KEY_DEVELOPER_KEY, SysConfig.DEVELOPERKEY);

        // 应用Key，此项必填，由捷通华声提供
        initparam.addParam(InitParam.PARAM_KEY_APP_KEY, SysConfig.APPKEY);

        // 配置日志参数
        String sdcardState = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(sdcardState)) {
            String sdPath = Environment.getExternalStorageDirectory().getAbsolutePath();
            String packageName = context.getPackageName();
            
            String logPath = sdPath + File.separator + "sinovoice"
                    + File.separator + packageName + File.separator + "log"
                    + File.separator;

            // 日志文件地址
            File fileDir = new File(logPath);
            if (!fileDir.exists()) {
                fileDir.mkdirs();
            }

            // 日志的路径，可选，如果不传或者为空则不生成日志
            initparam.addParam(InitParam.PARAM_KEY_LOG_FILE_PATH, logPath);

            // 日志数目，默认保留多少个日志文件，超过则覆盖最旧的日志
            initparam.addParam(InitParam.PARAM_KEY_LOG_FILE_COUNT, "5");

            // 日志大小，默认一个日志文件写多大，单位为K
            initparam.addParam(InitParam.PARAM_KEY_LOG_FILE_SIZE, "1024");

            // 日志等级，0=无，1=错误，2=警告，3=信息，4=细节，5=调试，SDK将输出小于等于logLevel的日志信息
            initparam.addParam(InitParam.PARAM_KEY_LOG_LEVEL, "5");
        }

        return initparam.getStringConfig();
    }

}
