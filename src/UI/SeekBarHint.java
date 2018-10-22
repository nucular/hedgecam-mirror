package com.caddish_hedgehog.hedgecam2.UI;

import com.caddish_hedgehog.hedgecam2.R;

import android.widget.TextView;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;

public class SeekBarHint extends TextView{
	private final RectF rect = new RectF();
	private final TextPaint paint = getPaint();
	private final int padding = getContext().getResources().getDimensionPixelSize(R.dimen.seekbar_hint_padding);
	private final int pointer = getContext().getResources().getDimensionPixelSize(R.dimen.seekbar_hint_pointer);
	private final float radius = getContext().getResources().getDimension(R.dimen.toast_radius);
	private final int color = getContext().getResources().getColor(R.color.toast_color);

	public SeekBarHint(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		// https://stackoverflow.com/questions/18387814/drawing-on-canvas-porterduff-mode-clear-draws-black-why
		setLayerType(LAYER_TYPE_SOFTWARE, null);
	}

	public SeekBarHint(Context context, AttributeSet attrs) {
		super(context, attrs);
		// see above
		setLayerType(LAYER_TYPE_SOFTWARE, null);
	}

	public SeekBarHint(Context context) {
		super(context);
		// see above
		setLayerType(LAYER_TYPE_SOFTWARE, null);
	}

	@Override
	protected void onMeasure( int widthMeasureSpec, int heightMeasureSpec )
	{
		super.onMeasure( widthMeasureSpec, heightMeasureSpec );
		
		int rotation = (int)getRotation();
		setTranslationX(-getMeasuredWidth()/2);
		if (rotation == 0 || rotation == 180) {
			setTranslationY(0);
		} else {
			setTranslationY(getMeasuredHeight()/2-getMeasuredWidth()/2);
		}
	}

	@Override 
	protected void onDraw(Canvas canvas) {
		paint.setStyle(Paint.Style.FILL);
		paint.setColor(color);

		rect.left = 0;
		rect.top = 0;
		rect.right = canvas.getWidth();
		rect.bottom = canvas.getHeight();
		Path path = new Path();
		final int height = pointer*2;
		if (getPaddingLeft() != padding) {
			rect.left = pointer;
			final int center = canvas.getHeight()/2;
			path.moveTo(0, center);
			path.lineTo(height, center-pointer);
			path.lineTo(height, center+pointer);
			path.lineTo(0, center);
		} else if (getPaddingTop() != padding) {
			rect.top = pointer;
			final int center = canvas.getWidth()/2;
			path.moveTo(center, 0);
			path.lineTo(center-pointer, height);
			path.lineTo(center+pointer, height);
			path.lineTo(center, 0);
		} else if (getPaddingRight() != padding) {
			rect.right -= pointer;
			final int center = canvas.getHeight()/2;
			final int start = canvas.getWidth();
			path.moveTo(start, center);
			path.lineTo(start-height, center-pointer);
			path.lineTo(start-height, center+pointer);
			path.lineTo(start, center);
		} else {
			rect.bottom -= pointer;
			final int center = canvas.getWidth()/2;
			final int start = canvas.getHeight();
			path.moveTo(center, start);
			path.lineTo(center-pointer, start-height);
			path.lineTo(center+pointer, start-height);
			path.lineTo(center, start);
		}

		path.close();
		canvas.drawPath(path, paint);
		paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
		canvas.drawRoundRect(rect, radius, radius, paint);
		paint.setXfermode(null);

		paint.setColor(Color.WHITE);
		
		super.onDraw(canvas);
	}

}
