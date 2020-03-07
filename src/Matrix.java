package com.caddish_hedgehog.hedgecam2;

public class Matrix {

	public static double[][] inverse(double[][] matrix) {
		double[][] inverse = new double[matrix.length][matrix[0].length];

		double t0 = matrix[1][1] * matrix[2][2] - matrix[2][1] * matrix[1][2];
		double t1 = matrix[2][0] * matrix[1][2] - matrix[1][0] * matrix[2][2];
		double t2 = matrix[1][0] * matrix[2][1] - matrix[2][0] * matrix[1][1];
		double det = matrix[0][0] * t0 + matrix[0][1] * t1 + matrix[0][2] * t2;
		if (Math.abs(det) < 1e-9) {
			return matrix;
		}
		inverse[0][0] = (float) (t0 / det);
		inverse[0][1] = (float) ((matrix[2][1] * matrix[0][2] - matrix[0][1] * matrix[2][2]) / det);
		inverse[0][2] = (float) ((matrix[0][1] * matrix[1][2] - matrix[1][1] * matrix[0][2]) / det);
		inverse[1][0] = (float) (t1 / det);
		inverse[1][1] = (float) ((matrix[0][0] * matrix[2][2] - matrix[2][0] * matrix[0][2]) / det);
		inverse[1][2] = (float) ((matrix[1][0] * matrix[0][2] - matrix[0][0] * matrix[1][2]) / det);
		inverse[2][0] = (float) (t2 / det);
		inverse[2][1] = (float) ((matrix[2][0] * matrix[0][1] - matrix[0][0] * matrix[2][1]) / det);
		inverse[2][2] = (float) ((matrix[0][0] * matrix[1][1] - matrix[1][0] * matrix[0][1]) / det);

		return inverse;
	}

	public static double[][] multiply(double[][] a, double[][] b) {
		if (a[0].length != b.length)
			throw new IllegalStateException("invalid dimensions");

		double[][] matrix = new double[a.length][b[0].length];
		for (int i = 0; i < a.length; i++) {
			for (int j = 0; j < b[0].length; j++) {
				double sum = 0;
				for (int k = 0; k < a[i].length; k++)
					sum += a[i][k] * b[k][j];
				matrix[i][j] = sum;
			}
		}

		return matrix;
	}
}