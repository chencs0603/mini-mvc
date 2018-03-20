package personal.chencs.practice.mvc.framework.util;

/**
 * 类名转换工具类
 *
 * @author: chencs
 * @date: 2018/3/20
 * @description:
 */
public class ClassNameUtils {

    /**
     * 通过simpleClassName获取默认ClassName
     * 第一个字母小写
     *
     * @param simpleClassName
     * @return
     */
    public static String defaultClassName(String simpleClassName) {
        char[] charArray = simpleClassName.toCharArray();
        charArray[0] += 32;
        return String.valueOf(charArray);
    }

}
