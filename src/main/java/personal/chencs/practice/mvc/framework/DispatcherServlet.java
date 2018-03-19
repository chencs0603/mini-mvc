package personal.chencs.practice.mvc.framework;

import personal.chencs.practice.mvc.framework.annotation.Controller;
import personal.chencs.practice.mvc.framework.annotation.RequestMapping;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class DispatcherServlet extends HttpServlet {

    private Properties properties = new Properties();

    private Map<String, Object> ioc = new HashMap<>();

    private List<String> classNames = new ArrayList<>();

    private Map<String, Method> handlerMapping = new HashMap<>();

    private Map<String, Object> controllerMap = new HashMap<>();

    @Override
    public void init(ServletConfig config) throws ServletException {

        loadConfigFile(config.getInitParameter("configLocation"));

        scanPackage(properties.getProperty("scanPackage"));

        newInstance();

        initHandleMapping();

    }

    /**
     * 加载配置文件
     *
     * @param configLocation 配置文件路径
     */
    private void loadConfigFile(String configLocation) {
        // 把web.xml中configLocation对应的文件加载进来，ClassLoader是从根路径上搜索文件
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(configLocation);
        try {
            // 用Properties加载文件里的内容
            properties.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 关流
            if (null != inputStream) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 扫描指定包名下的所有的类，将全类名加入classNames
     *
     * @param packageName 包名
     */
    private void scanPackage(String packageName) {
        // 把所有的.替换成/
        String packagePath = "/" + packageName.replaceAll("\\.", "/");
        URL url = this.getClass().getClassLoader().getResource(packagePath);
        // 遍历指定文件夹的所有文件
        File dir = new File(url.getFile());
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                // 递归读取包
                scanPackage(packageName + "." + file.getName());
            } else {
                // 将全类名加入classNames
                String className = packageName + "." + file.getName().replace(".class", "");
                classNames.add(className);
            }
        }
    }

    /**
     * 把classNames中加上注解的类实例化
     */
    private void newInstance() {
        // classNames为空
        if (classNames.isEmpty()) {
            return;
        }
        // 遍历classNames
        for (String className : classNames) {
            try {
                // 通过反射将带有@Controller注解的类实例化，并存入ioc容器中
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(Controller.class)) {
                    ioc.put(toLowerFirstWord(clazz.getSimpleName()), clazz.newInstance());
                } else {
                    continue;
                }
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }
        }
    }

    /**
     * handleMapping初始化
     */
    private void initHandleMapping() {
        // ioc为空
        if (ioc.isEmpty()) {
            return;
        }
        try {
            // 遍历ioc
            for (Map.Entry<String, Object> entry : ioc.entrySet()) {
                Class<? extends Object> clazz = entry.getValue().getClass();
                if (!clazz.isAnnotationPresent(Controller.class)) {
                    continue;
                }

                // 确定url时，要controller头的url加上方法上的url
                String baseUrl = "";
                if (clazz.isAnnotationPresent(RequestMapping.class)) {
                    RequestMapping annotation = clazz.getAnnotation(RequestMapping.class);
                    baseUrl = annotation.value();
                }
                // getMethods：本类以及父类的所有public方法
                // getDeclaredMethods：本类中的所有方法，
                Method[] methods = clazz.getMethods();
                // 遍历controller类的所有方法
                for (Method method : methods) {
                    if (!method.isAnnotationPresent(RequestMapping.class)) {
                        continue;
                    }
                    RequestMapping annotation = method.getAnnotation(RequestMapping.class);
                    String url = annotation.value();
                    // 确定url时，要controller头的加上方法上的
                    url = (baseUrl + "/" + url).replaceAll("/+", "/");
                    // 存放URL和对应的类和方法
                    handlerMapping.put(url, method);
                    controllerMap.put(url, clazz.newInstance());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        dispather(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        dispather(req, resp);
    }

    private void dispather(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (handlerMapping.isEmpty()) {
            return;
        }

        String url = req.getRequestURI();
        String contextPath = req.getContextPath();

        //拼接url并把多个/替换成一个
        url = url.replace(contextPath, "").replaceAll("/+", "/");

        if (!this.handlerMapping.containsKey(url)) {
            resp.getWriter().write("404 NOT FOUND!");
            return;
        }

        Method method = this.handlerMapping.get(url);

        //获取方法的参数列表
        Class<?>[] parameterTypes = method.getParameterTypes();

        //获取请求的参数
        Map<String, String[]> parameterMap = req.getParameterMap();

        //保存参数值
        Object[] paramValues = new Object[parameterTypes.length];

        //方法的参数列表
        for (int i = 0; i < parameterTypes.length; i++) {
            //根据参数名称，做某些处理
            String requestParam = parameterTypes[i].getSimpleName();


            if (requestParam.equals("HttpServletRequest")) {
                //参数类型已明确，这边强转类型
                paramValues[i] = req;
                continue;
            }
            if (requestParam.equals("HttpServletResponse")) {
                paramValues[i] = resp;
                continue;
            }
            if (requestParam.equals("String")) {
                for (Map.Entry<String, String[]> param : parameterMap.entrySet()) {
                    String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
                    paramValues[i] = value;
                }
            }
        }
        //利用反射机制来调用
        try {
            method.invoke(this.controllerMap.get(url), paramValues);//obj是method所对应的实例 在ioc容器中
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String toLowerFirstWord(String name) {
        char[] charArray = name.toCharArray();
        charArray[0] += 32;
        return String.valueOf(charArray);
    }

}
