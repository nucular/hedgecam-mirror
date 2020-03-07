#pragma version(1)
#pragma rs java_package_name(com.caddish_hedgehog.hedgecam2)
#pragma rs_fp_relaxed

int32_t *histogram_array;
int32_t *histogram_array_r;
int32_t *histogram_array_g;
int32_t *histogram_array_b;
uint32_t divider;
uint32_t max_value;

void clear_histogram() {
	for (int i = 0; i < 256/divider; i++)
		histogram_array[i] = 0;
}


void __attribute__((kernel)) histogram_brightness(uchar4 pixel, uint32_t x, uint32_t y) {
	float4 pixel_f = convert_float4(pixel);
	uchar pixel_mono = ((uchar)clamp(0.299f*pixel_f.r + 0.587f*pixel_f.g + 0.114f*pixel_f.b, 0.0f, 255.0f))/divider;
	
	rsAtomicInc(&histogram_array[pixel_mono]);
}

void __attribute__((kernel)) histogram_maximum(uchar4 pixel, uint32_t x, uint32_t y) {
	rsAtomicInc(&histogram_array[max(pixel.r, max(pixel.g, pixel.b))]);
}

void clear_histogram_rgb() {
	for (int i = 0; i < 256/divider; i++) {
		histogram_array_r[i] = 0;
		histogram_array_g[i] = 0;
		histogram_array_b[i] = 0;
	}
}

void __attribute__((kernel)) histogram_rgb(uchar4 pixel, uint32_t x, uint32_t y) {
	rsAtomicInc(&histogram_array_r[pixel.r/divider]);
	rsAtomicInc(&histogram_array_g[pixel.g/divider]);
	rsAtomicInc(&histogram_array_b[pixel.b/divider]);
}

int32_t *color;

void __attribute__((kernel)) color_probe(uchar4 pixel, uint32_t x, uint32_t y) {
	rsAtomicAdd(&color[0], pixel.r);
	rsAtomicAdd(&color[1], pixel.g);
	rsAtomicAdd(&color[2], pixel.b);
}
