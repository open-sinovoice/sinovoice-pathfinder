package com.sinovoice.pathfinder.hcicloud.hwr;

import com.sinovoice.hcicloudsdk.common.hwr.HwrRecogResult;

public interface OnHwrRecogResultChangedListener {
	public void onResultChanged(HwrRecogResult hwrRecogResult);
}
