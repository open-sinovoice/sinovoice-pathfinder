package com.sinovoice.pathfinder.hcicloud.hwr;

import android.content.Context;
import android.util.Log;

import com.sinovoice.hcicloudsdk.api.hwr.HciCloudHwr;
import com.sinovoice.hcicloudsdk.common.hwr.HwrInitParam;
import com.sinovoice.pathfinder.hcicloud.sys.SysConfig;

public class HciCloudHwrHelper {
    private static final String TAG = HciCloudHwrHelper.class.getSimpleName();

    private static HciCloudHwrHelper mInstance;

    private HciCloudHwrHelper() {
    }

    public static HciCloudHwrHelper getInstance() {
        if (mInstance == null) {
            mInstance = new HciCloudHwrHelper();
        }
        return mInstance;
    }

    /**
     * HWR手写识别能力初始化，返回的错误码可以在API中HciErrorCode查看
     * 
     * @param context
     * @return 错误码, return 0 表示成功
     */
    public int init(Context context) {
        int initResult = 0;

        // 构造Hwr初始化的参数类的实例
        HwrInitParam hwrInitParam = new HwrInitParam();

        // 获取App应用中的lib的路径,如果使用/data/data/pkgName/lib下的资源文件,需要添加android_so的标记
        String hwrDirPath = context.getFilesDir().getAbsolutePath()
                .replace("files", "lib");
        hwrInitParam.addParam(HwrInitParam.PARAM_KEY_DATA_PATH, hwrDirPath);
        hwrInitParam.addParam(HwrInitParam.PARAM_KEY_FILE_FLAG, "android_so");
        hwrInitParam.addParam(HwrInitParam.PARAM_KEY_INIT_CAP_KEYS,
                SysConfig.CAPKEY_HWR);

        Log.d(TAG, "hwr init config: " + hwrInitParam.getStringConfig());

        // HWR 初始化
        initResult = HciCloudHwr.hciHwrInit(hwrInitParam.getStringConfig());
        return initResult;
    }

    /**
     * HWR手写识别能力反初始化，返回的错误码可以在API中HciErrorCode查看
     * 
     * @return 错误码, return 0 表示成功
     */
    public int release() {
        int result = HciCloudHwr.hciHwrRelease();
        return result;
    }
}
