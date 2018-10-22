#pragma version(1)
#pragma rs java_package_name(com.caddish_hedgehog.hedgecam2)
#pragma rs_fp_relaxed

float alpha = 0.5;
rs_allocation mask;

uchar __attribute__((kernel)) monochrome(uchar4 pixel, uint32_t x, uint32_t y) {
	float4 pixel_f = convert_float4(pixel);

	return (uchar)clamp(0.299f*pixel_f.r + 0.587f*pixel_f.g + 0.114f*pixel_f.b, 0.0f, 255.0f);
}

uchar4 __attribute__((kernel)) unsharp_mask(uchar4 pixel, uint32_t x, uint32_t y) {
	float4 pixel_f = convert_float4(pixel);
	float mask_pixel = 127.0f-(float)(rsGetElementAt_uchar(mask, x, y));
	
	if (mask_pixel > 0) {
		float pixel_alpha = mask_pixel/127.0f*alpha;
		pixel.r = (uchar)clamp(pixel_f.r+(pixel_f.r*pixel_alpha+0.5f), 0.0f, 255.0f);
		pixel.g = (uchar)clamp(pixel_f.g+(pixel_f.g*pixel_alpha+0.5f), 0.0f, 255.0f);
		pixel.b = (uchar)clamp(pixel_f.b+(pixel_f.b*pixel_alpha+0.5f), 0.0f, 255.0f);
	} else if (mask_pixel < 0) {
		float pixel_alpha = mask_pixel/-127.0f*alpha;
		pixel.r = (uchar)clamp(pixel_f.r-((255.0f-pixel_f.r)*pixel_alpha+0.5f), 0.0f, 255.0f);
		pixel.g = (uchar)clamp(pixel_f.g-((255.0f-pixel_f.g)*pixel_alpha+0.5f), 0.0f, 255.0f);
		pixel.b = (uchar)clamp(pixel_f.b-((255.0f-pixel_f.b)*pixel_alpha+0.5f), 0.0f, 255.0f);
	}

	return pixel;
}
