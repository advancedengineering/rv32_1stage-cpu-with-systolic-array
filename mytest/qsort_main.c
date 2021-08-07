// See LICENSE for license details.

//**************************************************************************
// Quicksort benchmark
//--------------------------------------------------------------------------
//
// This benchmark uses quicksort to sort an array of integers. The
// implementation is largely adapted from Numerical Recipes for C. The
// input data (and reference data) should be generated using the
// qsort_gendata.pl perl script and dumped to a file named
// dataset1.h.

#include "util.h"
#include <string.h>
#include <assert.h>

// The INSERTION_THRESHOLD is the size of the subarray when the
// algorithm switches to using an insertion sort instead of
// quick sort.

#define INSERTION_THRESHOLD 10

// NSTACK is the required auxiliary storage.
// It must be at least 2*lg(DATA_SIZE)

#define NSTACK 50


#define type int
#define N 4

int A[N][N], B[N][N], C[N][N];
int D[N][N] = 
{
{0, 0, 0, 0},
{0, 4, 8, 12},
{0, 8, 16, 24},
{0, 12, 24, 36}
};

void init_matrix();

void serial_mul();


int main( int argc, char* argv[] )
{

	init_matrix();
	setStats(1);
	serial_mul();
	setStats(0);
	
	return verify(N*N, C, D);

}

void init_matrix(){
	for(int i = 0; i < N; i++){
		for(int j = 0; j < N; j++){
			A[i][j] = i;
		}
	}

	for(int i = 0; i < N; i++){
                for(int j = 0; j < N; j++){
                        B[i][j] = j;
                }
	}

	for(int i = 0; i < N; i++){
                for(int j = 0; j < N; j++){
                        C[i][j] = 0;
                }
        }
}

void serial_mul(){
	for (int i = 0; i < N; i++) {
       		for (int j = 0; j < N; j++) {
            		int temp = 0;
            		for (int k = 0; k < N; k++) {
                		temp += A[i][k] * B[k][j];
            		}
            		C[i][j] = temp;
        	}
    	}
}
