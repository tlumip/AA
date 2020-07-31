package com.pb.common.calculator;

import com.pb.common.matrix.Matrix;
import com.pb.common.calculator.DataEntry;

/**
 * Created by IntelliJ IDEA.
 * User: Jim
 * Date: Aug 5, 2008
 * Time: 8:30:39 AM
 * To change this template use File | Settings | File Templates.
 */
public interface MatrixDataServerIf {
    Matrix getMatrix( DataEntry matrixEntry );
    public String testRemote();
    public void clear();
}

