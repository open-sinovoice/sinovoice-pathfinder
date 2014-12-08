package com.sinovoice.pathfinder;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.inputmethodservice.Keyboard;

public class SVKeyboard extends Keyboard {

	public SVKeyboard(Context context, int xmlLayoutResId) {
		super(context, xmlLayoutResId);
		// TODO Auto-generated constructor stub
	}

	public SVKeyboard(Context context, int layoutTemplateResId,
			CharSequence characters, int columns, int horizontalPadding) {
		super(context, layoutTemplateResId, characters, columns, horizontalPadding);
		// TODO Auto-generated constructor stub
	}

	
	public static class SVKey extends Key {

		public SVKey(Resources res, Row parent, int x, int y,
				XmlResourceParser parser) {
			super(res, parent, x, y, parser);
			// TODO Auto-generated constructor stub
		}
		
	}
}
