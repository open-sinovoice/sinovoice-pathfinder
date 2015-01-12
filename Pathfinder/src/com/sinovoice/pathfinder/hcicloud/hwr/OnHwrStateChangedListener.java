package com.sinovoice.pathfinder.hcicloud.hwr;

import com.sinovoice.hcicloudsdk.common.hwr.HwrRecogResult;

public interface OnHwrStateChangedListener {
	public void onResultChanged(HwrRecogResult hwrRecogResult);
	
	public void onStartWriteNewWord();
}
