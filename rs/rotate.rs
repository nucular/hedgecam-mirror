#pragma version(1)
#pragma rs java_package_name(com.caddish_hedgehog.hedgecam2)
#pragma rs_fp_relaxed

rs_allocation alloc_in;

uint32_t max_x;
uint32_t max_y;

uchar4 __attribute__((kernel)) mirror(uint32_t x, uint32_t y) {
	return rsGetElementAt_uchar4(alloc_in, max_x-x, y);
}

uchar4 __attribute__((kernel)) rotate180(uint32_t x, uint32_t y) {
	return rsGetElementAt_uchar4(alloc_in, max_x-x, max_y-y);
}

uchar4 __attribute__((kernel)) rotate180_mirror(uint32_t x, uint32_t y) {
	return rsGetElementAt_uchar4(alloc_in, x, max_y-y);
}

uchar4 __attribute__((kernel)) rotate90(uint32_t x, uint32_t y) {
	return rsGetElementAt_uchar4(alloc_in, y, max_x-x);
}

uchar4 __attribute__((kernel)) rotate90_mirror(uint32_t x, uint32_t y) {
	const uint32_t offset = x*(max_y+1)+y;
    const uchar4 *out = rsGetElementAt(alloc_in, offset);
    return *out;
}

uchar4 __attribute__((kernel)) rotate270(uint32_t x, uint32_t y) {
	return rsGetElementAt_uchar4(alloc_in, max_y-y, x);
}

uchar4 __attribute__((kernel)) rotate270_mirror(uint32_t x, uint32_t y) {
	return rsGetElementAt_uchar4(alloc_in, max_y-y, max_x-x);
}
