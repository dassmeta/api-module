package io.nuls.api.core.constant;

import java.util.HashSet;
import java.util.Set;

public class NulsConstant {

    //黄牌惩罚
    public static final int PUBLISH_YELLOW = 1;
    //红牌惩罚
    public static final int PUTLISH_RED = 2;
    //尝试分叉
    public static final int TRY_FORK = 1;
    //打包双花交易
    public static final int DOUBLE_SPEND = 2;
    //太多黄牌惩罚
    public static final int TOO_MUCH_YELLOW_PUNISH = 3;
    //委托共识
    public static final int JOIN_CONSENSUS = 0;
    //取消委托共识
    public static final int CANCEL_CONSENSUS = 1;

    public static final int CONTRACT_STATUS_NORMAL = 0;

    public static final int CONTRACT_STATUS_FAIL = -1;

    public static final int CONTRACT_STATUS_APPROVING = 1;

    public static final int CONTRACT_STATUS_PASSED = 2;

    public static long BlOCKHEIGHT_TIME_DIVIDE = 1000000000000L;

    public static final int CONTRACT_NOT_EXIST = 100002;
}
