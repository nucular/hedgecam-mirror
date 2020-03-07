#pragma version(1)
#pragma rs java_package_name(com.caddish_hedgehog.hedgecam2)
#pragma rs_fp_relaxed

ushort divider = 1;

/*******************************
	Noise reduction stack
*******************************/
rs_allocation alloc_avg;

int offset_x = 0;
int offset_y = 0;

void __attribute__((kernel)) start(uchar4 pixel, uint32_t x, uint32_t y) {
	rsSetElementAt_ushort3(alloc_avg, convert_ushort3(pixel.rgb), x, y);
}

void __attribute__((kernel)) add(uchar4 pixel, uint32_t x, uint32_t y) {
	rsSetElementAt_ushort3(alloc_avg, rsGetElementAt_ushort3(alloc_avg, x, y)+convert_ushort3(pixel.rgb), x, y);
}

void __attribute__((kernel)) add_aligned(uchar4 pixel, uint32_t x, uint32_t y) {
	int real_x = ((int)x)-offset_x;
	int real_y = ((int)y)-offset_y;
	if( real_x >= 0 && real_y >= 0 && real_x < rsAllocationGetDimX(alloc_avg) && real_y < rsAllocationGetDimY(alloc_avg) ) {
		rsSetElementAt_ushort3(alloc_avg, rsGetElementAt_ushort3(alloc_avg, (uint32_t)real_x, (uint32_t)real_y)+convert_ushort3(pixel.rgb), (uint32_t)real_x, (uint32_t)real_y);
	} else {
		if (real_x < 0)
			real_x += rsAllocationGetDimX(alloc_avg);
		else if (real_x >= rsAllocationGetDimX(alloc_avg))
			real_x -= rsAllocationGetDimX(alloc_avg);

		if (real_y < 0)
			real_y += rsAllocationGetDimY(alloc_avg);
		else if (real_y >= rsAllocationGetDimY(alloc_avg))
			real_y -= rsAllocationGetDimY(alloc_avg);
			
		ushort3 p = rsGetElementAt_ushort3(alloc_avg, (uint32_t)real_x, (uint32_t)real_y);
		rsSetElementAt_ushort3(alloc_avg, p+p/divider, (uint32_t)real_x, (uint32_t)real_y);
	}
}

/*******************************
	Max and min level
*******************************/
uint32_t *max_min;

float fDivider = 1.0f;
float fMin = 0.0f;

void init_max_min() {
	max_min[0] = 0;
	max_min[1] = 65535;
}

void __attribute__((kernel)) calc_max(ushort3 pixel, uint32_t x, uint32_t y) {
	uint3 pixel_i = convert_uint3(pixel);
	uint32_t pixel_max = max(pixel_i.r, pixel_i.g);
	pixel_max = max(pixel_max, pixel_i.b);
	
	rsAtomicMax(&max_min[0], pixel_max);
}

void __attribute__((kernel)) calc_min_max(ushort3 pixel, uint32_t x, uint32_t y) {
	uint3 pixel_i = convert_uint3(pixel);
	uint32_t pixel_min = min(pixel_i.r, pixel_i.g);
	pixel_min = min(pixel_min, pixel_i.b);
	
	rsAtomicMin(&max_min[1], pixel_min);
	
	calc_max(pixel, x, y);
}

/*******************************
	Histogram
*******************************/
uint32_t histogram_width;
int *histogram_array;

void init_histogram() {
	for (int i = 0; i < histogram_width; i++)
		histogram_array[i] = 0;
}

void __attribute__((kernel)) histogram(ushort3 pixel, uint32_t x, uint32_t y) {
	uint3 pixel_i = convert_uint3(pixel);
	uint32_t pixel_max = max(pixel_i.r, pixel_i.g);
	pixel_max = max(pixel_max, pixel_i.b);
	
	rsAtomicInc(&histogram_array[pixel_max]);
}

/*******************************
	Finish nr image
*******************************/
uchar4 __attribute__((kernel)) finish_l(uint32_t x, uint32_t y) {
	float3 pixel = convert_float3(rsGetElementAt_ushort3(alloc_avg, x, y));

	float4 out;
    out.r = clamp(pixel.r/fDivider, 0.0f, 255.0f);
    out.g = clamp(pixel.g/fDivider, 0.0f, 255.0f);
    out.b = clamp(pixel.b/fDivider, 0.0f, 255.0f);
    out.a = 255.0f;

	return convert_uchar4(out);
}

uchar4 __attribute__((kernel)) finish_ls(uint32_t x, uint32_t y) {
	float3 pixel = convert_float3(rsGetElementAt_ushort3(alloc_avg, x, y));

	float4 out;
    out.r = clamp((pixel.r-fMin)/fDivider, 0.0f, 255.0f);
    out.g = clamp((pixel.g-fMin)/fDivider, 0.0f, 255.0f);
    out.b = clamp((pixel.b-fMin)/fDivider, 0.0f, 255.0f);
    out.a = 255.0f;

	return convert_uchar4(out);
}

uchar4 __attribute__((kernel)) finish(uint32_t x, uint32_t y) {
	ushort3 pixel = rsGetElementAt_ushort3(alloc_avg, x, y);

	ushort4 out;
    out.r = clamp(pixel.r/divider, 0, 255);
    out.g = clamp(pixel.g/divider, 0, 255);
    out.b = clamp(pixel.b/divider, 0, 255);
    out.a = 255;

	return convert_uchar4(out);
}

float gamma;

uchar4 __attribute__((kernel)) finish_boost(uint32_t x, uint32_t y) {
	float3 pixel = convert_float3(rsGetElementAt_ushort3(alloc_avg, x, y));

	float4 out;
    out.r = clamp(pow((pixel.r/fDivider), gamma)*255.0f, 0.0f, 255.0f);
    out.g = clamp(pow((pixel.g/fDivider), gamma)*255.0f, 0.0f, 255.0f);
    out.b = clamp(pow((pixel.b/fDivider), gamma)*255.0f, 0.0f, 255.0f);
    out.a = 255.0f;

	return convert_uchar4(out);
}

uchar4 __attribute__((kernel)) finish_histogram(uint32_t x, uint32_t y) {
	uint3 pixel = convert_uint3(rsGetElementAt_ushort3(alloc_avg, x, y));

	uint4 out;
    out.r = histogram_array[pixel.r];
    out.g = histogram_array[pixel.g];
    out.b = histogram_array[pixel.b];
    out.a = 255;

	return convert_uchar4(out);
}

uchar4 __attribute__((kernel)) crop(uint32_t x, uint32_t y) {
	return rsGetElementAt_uchar4(alloc_avg, x+offset_x, y+offset_y);
}
