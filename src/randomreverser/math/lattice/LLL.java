package randomreverser.math.lattice;

import randomreverser.math.component.BigMatrix;
import randomreverser.math.component.BigFraction;
import randomreverser.math.component.BigVector;

import java.math.BigInteger;

public class LLL {

    private LLL() {
    }

    private BigMatrix gramSchmidtBasis;
    private BigMatrix mu;
    private BigMatrix lattice;
    private BigMatrix H;
    private BigFraction[] sizes;
    private Params params;
    private int kmax;
    private int k;
    private boolean shouldUpdateGramSchmidt;
    private static final BigFraction eta = BigFraction.HALF;


    /**
     * LLL lattice reduction implemented as described on page 95 of Henri Cohen's
     * "A course in computational number theory"
     *
     * @param lattice the lattice to reduce
     * @param params  the parameters to be passed to LLL
     * @return the reduced lattice
     */
    public static Result reduce(BigMatrix lattice, Params params) {
        return new LLL().reduceLLL(lattice, params);
    }

    /**
     * LLL lattice reduction implemented as described on page 95 of Henri Cohen's
     * "A course in computational number theory"
     *
     * @param lattice the lattice to reduce
     * @param params  the parameters to be passed to LLL
     * @return the reduced lattice
     */
    public static Result reduceBKZ(BigMatrix lattice, int blockSize, Params params) {
        return new LLL().reduceBKZ0(lattice, blockSize, params);
    }

    private boolean passvec(BigVector v, int index, int dim) {
        int i;

        if (!v.get(index).equals(BigFraction.ONE)) {
            return false;
        }
        for (i = 0; i < dim; i++) {
            if (i != index && !v.get(i).equals(BigFraction.ZERO)) {
                return false;
            }
        }
        return true;
    }

    /**
     * BKZ reduces a lattice generated by a linearly independant set of vectors.
     *
     * @param lattice   the lattice to BKZ reduce
     * @param blockSize the blockSize to use in BKZ
     * @param params    the params for the LLL subroutine to reduce its basis to
     * @return a result encapsulating the lattice and the transformations done by BKZ
     */
    private Result reduceBKZ0(BigMatrix lattice, int blockSize, Params params) {
        int k, h;
        int z = 0;
        int j = 0;
        int beta = blockSize;
        Result result = reduceLLL(lattice, params);
        for (int row = 0; row < result.getNumDependantVectors(); row++) {
            lattice.setRow(row, result.getReducedBasis().getRow(row));
            mu.setRow(row, result.getGramSchmidtCoefficients().getRow(row));
            gramSchmidtBasis.setRow(row, result.getGramSchmidtBasis().getRow(row));
            sizes[row] = result.getGramSchmidtSizes()[row];
        }
        int dim = result.getReducedBasis().getRowCount();
        int colCount = result.getReducedBasis().getColumnCount();

        while (z < dim - 1) {
            j = (j % (dim - 1)) + 1;
            k = Math.min(j + beta - 1, dim);
            h = Math.min(k + 1, dim);
            BigVector v = enumerateBKZ(j - 1, k - 1, dim, sizes, mu);
            if (!passvec(v, j-1, dim)) {
                z = 0;
                BigVector newVec = new BigVector(dim);

                for (int l = 0; l < dim; l++) {
                    for (int s = j - 1; s <= k - 1; s++) {
                        //lattice[dim][l] += v[i] * lattice[i][l];
                        newVec.set(l, newVec.get(l).add(v.get(s).multiply(lattice.get(s, l))));
                    }
                }
                BigMatrix newBlock = new BigMatrix(h + 1, colCount);
                // set from 0 to j-2 so j-1 elements
                for (int row = 0; row <= j - 2; row++) {
                    newBlock.setRow(row, lattice.getRow(row));
                }
                // set j-1 (the jth element) with the new element
                newBlock.setRow(j - 1, newVec);
                // set j to h so h-j+1 elements (with j-1 to h-1)
                for (int row = j; row <= h; row++) {
                    newBlock.setRow(row, lattice.getRow(row - 1));
                }
                result = reduceLLL(newBlock, params);
                for (int row = 0; row < result.getNumDependantVectors(); row++) {
                    lattice.setRow(row, result.getReducedBasis().getRow(row));
                    mu.setRow(row, result.getGramSchmidtCoefficients().getRow(row));
                    gramSchmidtBasis.setRow(row, result.getGramSchmidtBasis().getRow(row));
                    sizes[row] = result.getGramSchmidtSizes()[row];
                }
            } else {
                z = z + 1;
                result = reduceLLL(lattice, params);
                for (int row = 0; row < result.getNumDependantVectors(); row++) {
                    lattice.setRow(row, result.getReducedBasis().getRow(row));
                    mu.setRow(row, result.getGramSchmidtCoefficients().getRow(row));
                    gramSchmidtBasis.setRow(row, result.getGramSchmidtBasis().getRow(row));
                    sizes[row] = result.getGramSchmidtSizes()[row];
                }
            }
        }
        return result;
    }

    private BigVector enumerateBKZ(int ini, int fim, int dim, BigFraction[] B, BigMatrix blockMu) {
        BigFraction[] cT = new BigFraction[dim + 1];
        BigFraction[] y = new BigFraction[dim + 1];

        BigInteger[] v = new BigInteger[dim + 1];
        BigInteger[] delta = new BigInteger[dim + 1];
        BigInteger[] d = new BigInteger[dim + 1];
        BigVector u = new BigVector(dim + 1);
        BigInteger[] uT = new BigInteger[dim + 1];
        BigInteger auxUT;
        BigFraction cL, auxY;


        // Initialize vectors
        cL = B[ini];
        uT[ini] = BigInteger.ONE;
        u.set(ini, BigFraction.ONE);
        y[ini] = BigFraction.ZERO;
        delta[ini] = BigInteger.ZERO;
        int s = ini;
        int t = ini;
        d[ini] = BigInteger.ONE;

        // non cited
        v[ini] = BigInteger.ZERO;

        for (int i = ini + 1; i <= fim + 1; i++) {
            uT[i] = delta[i] = v[i] = BigInteger.ZERO;
            u.set(i, BigFraction.ZERO);
            cT[i] = y[i] = BigFraction.ZERO;
            d[i] = BigInteger.ONE;
        }
        while (t <= fim) {

            // cT[t] = cT[t + 1] + (auxY[t] - 2*uT[t]*y[t] + auxUT[t]) * B[t];
            // cT(t) := cT(t+1) + (y(t) + u(t))^2 * c(t)  but (y(t)+u(t))^2= y(t)^2 + u(t)^2 + 2*u(t)*y(t)
            auxY = y[t].multiply(y[t]); // this is done to overcome loss in precision remember how they cumulate...
            auxUT = uT[t].multiply(uT[t]);
            cT[t] = cT[t + 1].add((auxY.add(y[t].multiply(uT[t]).multiply(BigInteger.TWO)).add(auxUT)).multiply(B[t]));
            if (cT[t].compareTo(cL) < 0) {
                if (t > ini) {
                    t--;
                    y[t] = BigFraction.ZERO;
                    for (int i = t + 1; i <= s; i++) {
                        y[t] = y[t].add(blockMu.get(i, t).multiply(uT[i]));
                    }
                    uT[t] = v[t] = y[t].round().negate();
                    delta[t] = BigInteger.ZERO;
                    // if (uT[t] > -y[t])
                    if (y[t].negate().compareTo(uT[t]) < 0) {
                        d[t] = BigInteger.ONE.negate();
                    } else {
                        d[t] = BigInteger.ONE;
                    }
                } else {
                    cL = cT[ini];
                    for (int j = ini; j <= fim; j++) {
                        u.set(j, new BigFraction(uT[j]));
                    }
                }
            } else {
                t++;
                s = Math.max(s, t); //Get max value
                if (t < s) {
                    delta[t] = delta[t].negate();
                }
                if (delta[t].multiply(d[t]).compareTo(BigInteger.ZERO) >= 0) {
                    delta[t] = delta[t].add(d[t]);
                }
                uT[t] = v[t].add(delta[t]);
            }
        }
        return u;
    }

    private Result reduceLLL(BigMatrix lattice, Params params) {
        this.params = params;
        int n = lattice.getRowCount();
        int m = lattice.getColumnCount();
        gramSchmidtBasis = new BigMatrix(n, m);
        mu = new BigMatrix(n, n);
        k = 1;
        kmax = 0;
        gramSchmidtBasis.setRow(0, lattice.getRow(0).copy());
        shouldUpdateGramSchmidt = true;
        H = BigMatrix.identityMatrix(n);
        this.lattice = lattice.copy();
        sizes = new BigFraction[n];
        sizes[0] = this.lattice.getRow(0).magnitudeSq();

        while (k < n) {
            if (k > kmax && shouldUpdateGramSchmidt) {
                kmax = k;
                incGramSchmidt();
            }
            testCondition();
        }

        int p = 0;
        for (int i = 0; i < n; i++) {
            if (this.lattice.getRow(i).isZero()) {
                p++;
            }
        }

        //remove all zero vectors
        BigMatrix nonZeroLattice = this.lattice.submatrix(p, 0, n - p, m);

        //update the other local variables to match the removal of zero vectors. Only needed when called as subroutine
        //of BKZ, should perhaps be removed when LLL alone is called. //TODO
        this.lattice = nonZeroLattice;
        gramSchmidtBasis = gramSchmidtBasis.submatrix(p, 0, n - p, m);
        mu = mu.submatrix(p, p, n - p, n - p);
        BigFraction[] nonZeroSizes = new BigFraction[n - p];
        for (int i = 0; i < n - p; i++) {
            nonZeroSizes[i] = sizes[i + p];
        }
        sizes = nonZeroSizes;
        return new Result(p, nonZeroLattice, H).setGramSchmidtInfo(gramSchmidtBasis, mu, sizes);
    }

    private void incGramSchmidt() {
        for (int j = 0; j <= k - 1; j++) {
            if (sizes[j].compareTo(BigFraction.ZERO) != 0) {
                mu.set(k, j, lattice.getRow(k).dot(gramSchmidtBasis.getRow(j)).divide(sizes[j]));
            } else {
                mu.set(k, j, BigFraction.ZERO);
            }
        }
        BigVector newRow = lattice.getRow(k).copy();
        for (int i = 0; i <= k - 1; i++) {
            newRow.subtractEquals(gramSchmidtBasis.getRow(i).multiply(mu.get(k, i)));
        }
        gramSchmidtBasis.setRow(k, newRow);
        sizes[k] = newRow.magnitudeSq();
    }

    private void testCondition() {
        red(k, k - 1);
        if (sizes[k].toDouble() < ((params.delta - (mu.get(k, k - 1).multiply(mu.get(k, k - 1))).toDouble()) * (sizes[k - 1]).toDouble())) { //TODO I don't trust this comparison as doubles
            swapg(k);
            k = Math.max(1, k - 1);
            shouldUpdateGramSchmidt = false;
        } else {
            shouldUpdateGramSchmidt = true;
            for (int l = k - 2; l >= 0; l--) {
                red(k, l);
            }
            k = k + 1;
        }
    }

    private void swapg(int n) {
        lattice.swapRowsEquals(n, n - 1);
        H.swapRowsEquals(n, n - 1);

        if (n > 1) {
            for (int j = 0; j <= n - 2; j++) {
                BigFraction temp = mu.get(n, j);
                mu.set(n, j, mu.get(n - 1, j));
                mu.set(n - 1, j, temp);
            }
        }
        BigFraction mutwopointoh = mu.get(n, n - 1);
        BigFraction B = sizes[n].add(mutwopointoh.multiply(mutwopointoh).multiply(sizes[n - 1]));

        if (sizes[n].equals(BigFraction.ZERO) && mutwopointoh.equals(BigFraction.ZERO)) {
            BigFraction temp = sizes[n];
            sizes[n] = sizes[n - 1];
            sizes[n - 1] = temp;
            gramSchmidtBasis.swapRowsEquals(n, n - 1);
            for (int i = n + 1; i <= kmax; i++) {
                temp = mu.get(i, n);
                mu.set(i, n, mu.get(i, n - 1));
                mu.set(i, n - 1, temp);
            }
        } else if (sizes[n].equals(BigFraction.ZERO)) {
            sizes[n - 1] = B;
            gramSchmidtBasis.getRow(n - 1).multiplyEquals(mutwopointoh);
            mu.set(n, n - 1, BigFraction.ONE.divide(mutwopointoh));
            for (int i = n + 1; i <= kmax; i++) {
                mu.set(i, n - 1, mu.get(i, n - 1).divide(mutwopointoh));
            }
        } else {
            BigFraction t = sizes[n - 1].divide(B);
            mu.set(n, n - 1, mutwopointoh.multiply(t));
            BigVector b = gramSchmidtBasis.getRow(n - 1).copy();
            gramSchmidtBasis.setRow(n - 1, gramSchmidtBasis.getRow(n).add(b.multiply(mutwopointoh)));
            gramSchmidtBasis.setRow(n, (b.multiply(sizes[k].divide(B))
                    .subtract(gramSchmidtBasis.getRow(n).multiply(mu.get(n, n - 1)))));
            sizes[n] = sizes[n].multiply(t);
            sizes[n - 1] = B;
            for (int i = n + 1; i <= kmax; i++) {
                t = mu.get(i, n);
                mu.set(i, n, mu.get(i, n - 1).subtract(mutwopointoh.multiply(t)));
                mu.set(i, n - 1, t.add(mu.get(n, n - 1).multiply(mu.get(i, n))));
            }
        }
    }

    private void red(int n, int l) {
        if (mu.get(n, l).abs().compareTo(eta) <= 0) {
            return;
        }
        BigFraction q = new BigFraction(mu.get(n, l).round());
        lattice.setRow(n, lattice.getRow(n).subtract(lattice.getRow(l).multiply(q)));
        H.setRow(n, H.getRow(n).subtract(H.getRow(l).multiply(q)));
        mu.set(n, l, mu.get(n, l).subtract(q));
        for (int i = 0; i <= l - 1; i++) {
            mu.set(n, i, mu.get(n, i).subtract(mu.get(l, i).multiply(q)));
        }
    }

    public static final class Params {
        protected double delta = 0.75;
        protected boolean debug;

        public Params setDelta(double delta) {
            this.delta = delta;
            return this;
        }

        public Params setDebug(boolean debug) {
            this.debug = debug;
            return this;
        }
    }

    public static final class Result {
        private int numDependantVectors;
        private BigMatrix reducedBasis;
        private BigMatrix transformationsDone;
        private BigMatrix gramSchmidtBasis;
        private BigMatrix gramSchmidtCoefficients;
        private BigFraction[] gramSchmidtSizes;

        private Result(int numDependantVectors, BigMatrix reducedBasis, BigMatrix transformationsDone) {
            this.numDependantVectors = numDependantVectors;
            this.reducedBasis = reducedBasis;
            this.transformationsDone = transformationsDone;
        }

        private Result setGramSchmidtInfo(BigMatrix gramSchmidtBasis, BigMatrix GSCoefficients, BigFraction[] GSSizes) {
            this.gramSchmidtBasis = gramSchmidtBasis;
            this.gramSchmidtCoefficients = GSCoefficients;
            this.gramSchmidtSizes = GSSizes;
            return this;
        }

        public int getNumDependantVectors() {
            return numDependantVectors;
        }

        public BigMatrix getReducedBasis() {
            return reducedBasis;
        }

        public BigMatrix getTransformations() {
            return transformationsDone;
        }

        public BigMatrix getGramSchmidtBasis() {
            return gramSchmidtBasis;
        }

        public BigMatrix getGramSchmidtCoefficients() {
            return gramSchmidtCoefficients;
        }

        public BigFraction[] getGramSchmidtSizes() {
            return gramSchmidtSizes;
        }
    }

}
