package personal.chencs.practice.mvc.framework;

import personal.chencs.practice.mvc.framework.annotation.Controller;
import personal.chencs.practice.mvc.framework.annotation.RequestMapping;
import personal.chencs.practice.mvc.framework.util.ClassNameUtils;

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
    // 存储配置文件的键值对
    private Properties properties = new Properties();
    // 存储controller实例
    private Map<String, Object> ioc = new HashMap<>();
    // 存储指定包名下的所有类，TODO:这个变量可以去掉
    private List<String> classNames = new ArrayList<>();
    // 存储URL与方法的映射关系
    private Map<String, Method> handlerMapping = new HashMap<>();
    // 存储URL与controller实例的映射关系
    private Map<String, Object> controllerMap = new HashMap<>();

    // TODO:添加日志

    @Override
    public void init(ServletConfig config) throws ServletException {
        // 加载配置文件
        loadConfigFile(config.getInitParameter("configLocation"));
        // 描述指定包名下的所有类
        scanPackage(properties.getProperty("scanPackage"));
        // 将带有Controller注解的类实例化
        newInstance();
        // 找出URL对应的Controller类和方法名
        initHandleMapping();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
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
            // 关闭流
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
        // 把所有的.替换成/，TODO:正则表达式
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
                    ioc.put(ClassNameUtils.defaultClassName(clazz.getSimpleName()), clazz.newInstance());
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
                Class<?> clazz = entry.getValue().getClass();
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

    /**
     * 处理请求
     * @param req 请求
     * @param resp 响应
     * @throws ServletException
     * @throws IOException
     */
    private void processRequest(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 没有URL映射
        if (handlerMapping.isEmpty()) {
            return;
        }
        // 获取请求的URL：去掉URL中的contextpath,并去掉多余的/
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");
        // 没找到URL对应的处理方法
        if (!this.handlerMapping.containsKey(url)) {
            resp.getWriter().write("404 NOT FOUND!");
            return;
        }

        Method method = this.handlerMapping.get(url);
        // 将请求中的参数映射到方法中的参数
        Map<String, String[]> paramMap = req.getParameterMap();
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] paramValues = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            // 请求和响应参数直接赋值
            String parameterTypeName = paramTypes[i].getSimpleName();
            if (parameterTypeName.equals("HttpServletRequest")) {
                paramValues[i] = req;
                continue;
            }
            if (parameterTypeName.equals("HttpServletResponse")) {
                paramValues[i] = resp;
                continue;
            }
            // TODO：目前只考虑String类型的参数转换，还应考虑bean对象的参数映射
            if (parameterTypeName.equals("String")) {
                for (Map.Entry<String, String[]> param : paramMap.entrySet()) {
                    // 先去掉[],然后在将，换成and
                    String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", " and ");
                    paramValues[i] = value;
                }
            }
        }
        // 通过反射调用对应controller实例的对应方法
        try {
            method.invoke(this.controllerMap.get(url), paramValues);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
