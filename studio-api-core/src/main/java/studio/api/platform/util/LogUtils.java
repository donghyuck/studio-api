package studio.api.platform.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import studio.api.platform.i18n.PlatformLogLocalizer;
import studio.api.platform.i18n.PlatformLogLocalizer.MessageCode;
import studio.echoes.platform.component.State;
import studio.echoes.platform.constant.Colors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LogUtils {

    public static String logMessageWithCode(String code, Object... args) {
        Object[] extendedArgs = new Object[args.length + 1];
        extendedArgs[0] = code;
        System.arraycopy(args, 0, extendedArgs, 1, args.length);  
        return PlatformLogLocalizer.getInstance().format(code, extendedArgs);
    }

    public static String logComponentDisabled(@SuppressWarnings("rawtypes") Class clazz, boolean simple){
        return PlatformLogLocalizer.getInstance().format(MessageCode.COMPONENT_DISABLED.code(), Colors.format(Colors.BLUE, simple?clazz.getSimpleName():clazz.getName()) );
    }

    public static String logComponentIntitalized(@SuppressWarnings("rawtypes") Class clazz, State state, boolean hasError, boolean simple){
        if( hasError)
            return PlatformLogLocalizer.getInstance().format(MessageCode.COMPONENT_INITIALIZED_WITH_ERRORS.code(), Colors.format(Colors.BLUE, simple?clazz.getSimpleName():clazz.getName() ), Colors.format(Colors.RED, state.name()) );
        else
            return PlatformLogLocalizer.getInstance().format(MessageCode.COMPONENT_STATE.code(), Colors.format(Colors.BLUE, simple?clazz.getSimpleName():clazz.getName()  ), Colors.format(Colors.RED, state.name()) );
    }

    public static String logComponentState(@SuppressWarnings("rawtypes") Class clazz, State state){
        return PlatformLogLocalizer.getInstance().format(MessageCode.COMPONENT_STATE.code(), Colors.format(Colors.BLUE, clazz.getName()), Colors.format(Colors.RED, state.name()) );
    }

    public static String logComponentState(@SuppressWarnings("rawtypes") Class clazz, State state, boolean simple){
        return PlatformLogLocalizer.getInstance().format(MessageCode.COMPONENT_STATE.code(), Colors.format(Colors.BLUE, simple?clazz.getSimpleName():clazz.getName()), Colors.format(Colors.RED, state.name()) );
    }

    public static String logComponentState(@SuppressWarnings("rawtypes") Class clazz, State from, State to, boolean simple){
        return PlatformLogLocalizer.getInstance().format(MessageCode.COMPONENT_STATE_CAHNGE.code(), Colors.format(Colors.BLUE, simple?clazz.getSimpleName():clazz.getName()), Colors.format(Colors.RED, from.name()), Colors.format(Colors.RED, to.name()) );
    }

    public static String logComponentMessage(@SuppressWarnings("rawtypes") Class clazz, Object msg1, boolean simple){
        return PlatformLogLocalizer.getInstance().format(MessageCode.COMPONENT_MESSAGE_ONE.code(), Colors.format(Colors.BLUE, simple?clazz.getSimpleName():clazz.getName()), msg1 );
    }

    public static String logComponentMessage(@SuppressWarnings("rawtypes") Class clazz, Object msg1, Object msg2, boolean simple){
        return PlatformLogLocalizer.getInstance().format(MessageCode.COMPONENT_MESSAGE_TWO.code(), Colors.format(Colors.BLUE, simple?clazz.getSimpleName():clazz.getName()), msg1, msg2 );
    }

    public static String logComponentMessage(@SuppressWarnings("rawtypes") Class clazz, Object msg1, Object msg2, Object msg3, boolean simple){
        return PlatformLogLocalizer.getInstance().format(MessageCode.COMPONENT_MESSAGE_THREE.code(), Colors.format(Colors.BLUE, simple?clazz.getSimpleName():clazz.getName()), msg1, msg2, msg3 );
    }

    public static String logComponentMessage(@SuppressWarnings("rawtypes") Class clazz, Object msg1, Object msg2, Object msg3, Object msg4, boolean simple){
        return PlatformLogLocalizer.getInstance().format(MessageCode.COMPONENT_MESSAGE_THREE.code(), Colors.format(Colors.BLUE, simple?clazz.getSimpleName():clazz.getName()), msg1, msg2, msg3 , msg4);
    }

    public static String logCustomComponentState(@SuppressWarnings("rawtypes") Class clazz, State state){
        return PlatformLogLocalizer.getInstance().format(MessageCode.CUSTOM_COMPONENT_STATE.code(), Colors.format(Colors.YELLOW, clazz.getName()), Colors.format(Colors.RED, state.name()) );
    }

    public static String logServiceState(@SuppressWarnings("rawtypes") Class clazz, State state){
       return PlatformLogLocalizer.getInstance().format(MessageCode.SERVICE_STATE.code(), Colors.format(Colors.GREEN, clazz.getName()), Colors.format(Colors.RED, state.name()) );
    }


}
