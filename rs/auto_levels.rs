#pragma version(1)
#pragma rs java_package_name(com.caddish_hedgehog.hedgecam2)
#pragma rs_fp_relaxed

uint32_t *max_min;

float fDivider = 1.0f;
float fMin = 0.0f;

void init_max_min() {
	max_min[0] = 0;
	max_min[1] = 255;
}

void __attribute__((kernel)) calc_max(uchar4 pixel, uint32_t x, uint32_t y) {
	uchar pixel_max = max(pixel.r, pixel.g);
	pixel_max = max(pixel_max, pixel.b);

	rsAtomicMax(&max_min[0], (uint32_t)pixel_max);
}

void __attribute__((kernel)) calc_min_max(uchar4 pixel, uint32_t x, uint32_t y) {
	uchar pixel_min = min(pixel.r, pixel.g);
	pixel_min = min(pixel_min, pixel.b);

	rsAtomicMin(&max_min[1], (uint32_t)pixel_min);

	calc_max(pixel, x, y);
}

uchar4 __attribute__((kernel)) auto_l(uchar4 pixel, uint32_t x, uint32_t y) {
	float4 pixel_f = convert_float4(pixel);

    pixel.r = (uchar)clamp(pixel_f.r/fDivider, 0.0f, 255.0f);
    pixel.g = (uchar)clamp(pixel_f.g/fDivider, 0.0f, 255.0f);
    pixel.b = (uchar)clamp(pixel_f.b/fDivider, 0.0f, 255.0f);

	return pixel;
}

uchar4 __attribute__((kernel)) auto_ls(uchar4 pixel, uint32_t x, uint32_t y) {
	float4 pixel_f = convert_float4(pixel);

    pixel.r = (uchar)clamp((pixel_f.r-fMin)/fDivider, 0.0f, 255.0f);
    pixel.g = (uchar)clamp((pixel_f.g-fMin)/fDivider, 0.0f, 255.0f);
    pixel.b = (uchar)clamp((pixel_f.b-fMin)/fDivider, 0.0f, 255.0f);

	return pixel;
}
