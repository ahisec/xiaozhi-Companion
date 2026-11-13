package cn.bigfish.phone;

public final class DirectDial {
    public interface Gateway {
        boolean allowed();
        void call(String number);
    }
    private final Gateway gateway;
    private String lastNumber = "";
    private long lastCall;

    public DirectDial(Gateway gateway) { this.gateway = gateway; }

    public synchronized String dial(String recipient, String contacts, long now) {
        String number = Protocol.resolveNumber(recipient, contacts);
        if (!gateway.allowed()) throw new IllegalStateException("请先在手机应用点击“授权直接拨号”，允许电话权限后重新发起呼叫");
        if (number.equals(lastNumber) && now - lastCall < 5000)
            throw new IllegalStateException("刚刚已向系统提交相同号码，已忽略五秒内重复请求；请查看手机通话状态");
        gateway.call(number);
        lastNumber = number;lastCall = now;
        return "已向 Android 电话服务提交直接呼叫，无需在伴侣应用确认。系统可能要求选择 SIM 卡或处理锁屏限制；工具无法确认是否已拨出、接通或结束，请勿声称已接通。";
    }
}
