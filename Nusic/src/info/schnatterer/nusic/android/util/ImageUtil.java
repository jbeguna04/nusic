package info.schnatterer.nusic.android.util;

import info.schnatterer.nusic.Constants;

import java.io.InputStream;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;

public class ImageUtil {
	private ImageUtil() {
	}

	public static Bitmap createScaledBitmap(InputStream inputStream,
			Context context) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			return createScaledBitmapLegacy(inputStream, context);
		} else {
			return createScaledBitmapModern(inputStream, context);
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private static Bitmap createScaledBitmapModern(InputStream inputStream,
			Context context) {
		Bitmap artwork = BitmapFactory.decodeStream(inputStream);
		if (artwork != null) {
			artwork = Bitmap.createScaledBitmap(
					artwork,
					(int) context.getResources().getDimension(
							android.R.dimen.notification_large_icon_width),
					(int) context.getResources().getDimension(
							android.R.dimen.notification_large_icon_height),
					false);
		} else {
			Log.d(Constants.LOG,
					"Unable to read bitmap from stream. Stream null?");
		}
		return artwork;
	}

	private static Bitmap createScaledBitmapLegacy(InputStream inputStream,
			Context context) {
		/*
		 * As we don't know the size of the notification icon bellow API lvl 11,
		 * theses devices will just use the standard icon.
		 */
		return null;
	}
}
