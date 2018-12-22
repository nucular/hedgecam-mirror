#pragma version(1) 
#pragma rs java_package_name(com.caddish_hedgehog.hedgecam2)

int32_t width;
int32_t height;

int32_t y_pixel_stride, y_row_stride, uv_pixel_stride, uv_row_stride;

rs_allocation inY,inU,inV;

uchar4 __attribute__((kernel)) YUV420ToRGB(uint32_t x, uint32_t y) {
	uint uvIndex = uv_pixel_stride * (x/2) + uv_row_stride * (y/2);

	float Y = (float)rsGetElementAt_uchar(inY, y_pixel_stride * x + y_row_stride * y);
	float U = (float)rsGetElementAt_uchar(inU, uvIndex)-128;
	float V = (float)rsGetElementAt_uchar(inV, uvIndex)-128;

	int4 argb;
	argb.r = (int)(Y + V * 1.40625f);
	argb.g = (int)(Y - U * 0.34375f - V * 0.71875f);
	argb.b = (int)(Y + U * 1.765625f);
	argb.a = 255;

	uchar4 out = convert_uchar4(clamp(argb, 0, 255));

	return out;

//	return rsYuvToRGBA_uchar4(rsGetElementAt_uchar(inY, y_pixel_stride * x + y_row_stride * y), rsGetElementAt_uchar(inU, uvIndex), rsGetElementAt_uchar(inV, uvIndex));
}

uchar4 __attribute__((kernel)) YUV420ToRGB_wide_range(uint32_t x, uint32_t y) {
	uint uvIndex = uv_pixel_stride * (x/2) + uv_row_stride * (y/2);

	float Y = (float)rsGetElementAt_uchar(inY, y_pixel_stride * x + y_row_stride * y);
	float U = (float)rsGetElementAt_uchar(inU, uvIndex)-128;
	float V = (float)rsGetElementAt_uchar(inV, uvIndex)-128;

	int4 argb;
	argb.r = (int)((Y + V * 1.40625f) * 0.95f);
	argb.g = (int)((Y - U * 0.34375f - V * 0.71875f) * 0.95f);
	argb.b = (int)((Y + U * 1.765625f) * 0.95f);
	argb.a = 255;

	uchar4 out = convert_uchar4(clamp(argb, 0, 255));

	return out;
}

uchar4 __attribute__((kernel)) YUV420ToRGB_saturated(uint32_t x, uint32_t y) {
	uint uvIndex = uv_pixel_stride * (x/2) + uv_row_stride * (y/2);

	float Y = (float)rsGetElementAt_uchar(inY, y_pixel_stride * x + y_row_stride * y);
	float U = (float)rsGetElementAt_uchar(inU, uvIndex)-128;
	float V = (float)rsGetElementAt_uchar(inV, uvIndex)-128;

	int4 argb;
	argb.r = (int)((Y + V * 1.6875f) * 0.95f);
	argb.g = (int)((Y - U * 0.4125f - V * 0.8625f) * 0.95f);
	argb.b = (int)((Y + U * 2.11875f) * 0.95f);
	argb.a = 255;

	uchar4 out = convert_uchar4(clamp(argb, 0, 255));
	
	return out;
}

