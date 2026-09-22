package com.shop.account;

/**
 * 账号仓储抽象 —— 把「账号存哪张表、怎么更新」这件事从观察者里抽出来。
 *
 * ★ 为什么需要它：
 *   观察者只该关心「有人登录了」，不该关心「这个人是员工还是买家」。
 *   有了这层抽象，UserStatusObserver 里的 EmployeeMapper 就换成了 AccountStore，
 *   加一种新用户（比如第三方登录用户）只需要多写一个实现类，观察者一行都不用改。
 *
 * ★ 它算不算设计模式？
 *   严格说它是「策略模式」的一个轻量应用（同一行为的不同实现，按 key 运行时选择）。
 *   在项目里它的定位是支撑观察者复用的基础设施，答辩时顺口提一句即可，不用当考点讲。
 */
public interface AccountStore {

    /** 这个实现负责哪类用户，要和 UserType 里的常量对上 */
    String getType();

    /** 登录成功后：更新最后登录时间 + 累计登录次数 +1 */
    void touchLogin(Long userId, String loginTime);

    /** 注册成功后：初始化账号资料（头像等） */
    void initProfile(Long userId);
}
