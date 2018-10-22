#pragma version(1)
#pragma rs java_package_name(com.caddish_hedgehog.hedgecam2)
#pragma rs_fp_relaxed

rs_allocation alloc_avg;
uint32_t *max_min;
ushort divider = 1;

float fDivider = 1.0f;
float fMin = 0.0f;

void __attribute__((kernel)) start(uchar4 pixel, uint32_t x, uint32_t y) {
	rsSetElementAt_ushort3(alloc_avg, convert_ushort3(pixel.rgb), x, y);
}

void __attribute__((kernel)) add(uchar4 pixel, uint32_t x, uint32_t y) {
	rsSetElementAt_ushort3(alloc_avg, rsGetElementAt_ushort3(alloc_avg, x, y)+convert_ushort3(pixel.rgb), x, y);
}

void init_max_min() {
	max_min[0] = 0;
	max_min[1] = 65535;
}

void __attribute__((kernel)) calc_max(ushort3 pixel, uint32_t x, uint32_t y) {
	ushort pixel_max = max(pixel.r, pixel.g);
	pixel_max = max(pixel_max, pixel.b);
	
	rsAtomicMax(&max_min[0], (uint32_t)pixel_max);
}

void __attribute__((kernel)) calc_min_max(ushort3 pixel, uint32_t x, uint32_t y) {
	ushort pixel_min = min(pixel.r, pixel.g);
	pixel_min = min(pixel_min, pixel.b);
	
	rsAtomicMin(&max_min[1], (uint32_t)pixel_min);
	
	calc_max(pixel, x, y);
}

uchar4 __attribute__((kernel)) finish_l(uint32_t x, uint32_t y) {
	float3 pixel = convert_float3(rsGetElementAt_ushort3(alloc_avg, x, y));

	uchar4 out;
    out.r = (uchar)clamp(pixel.r/fDivider, 0.0f, 255.0f);
    out.g = (uchar)clamp(pixel.g/fDivider, 0.0f, 255.0f);
    out.b = (uchar)clamp(pixel.b/fDivider, 0.0f, 255.0f);
    out.a = 255;

	return out;
}

uchar4 __attribute__((kernel)) finish_ls(uint32_t x, uint32_t y) {
	float3 pixel = convert_float3(rsGetElementAt_ushort3(alloc_avg, x, y));

	uchar4 out;
    out.r = (uchar)clamp((pixel.r-fMin)/fDivider, 0.0f, 255.0f);
    out.g = (uchar)clamp((pixel.g-fMin)/fDivider, 0.0f, 255.0f);
    out.b = (uchar)clamp((pixel.b-fMin)/fDivider, 0.0f, 255.0f);
    out.a = 255;

	return out;
}

uchar4 __attribute__((kernel)) finish(uint32_t x, uint32_t y) {
	ushort3 pixel = rsGetElementAt_ushort3(alloc_avg, x, y);

	uchar4 out;
    out.r = (uchar)clamp(pixel.r/divider, 0, 255);
    out.g = (uchar)clamp(pixel.g/divider, 0, 255);
    out.b = (uchar)clamp(pixel.b/divider, 0, 255);
    out.a = 255;

	return out;
}
