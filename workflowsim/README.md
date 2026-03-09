## Getting Started

1. Configure Redis in `Constants.java`

    ```java
    // redis
    public static final String HOST = "localhost";
    public static final int PORT = 6379;
    public static final String PASSWORD = "123456";
    ```

2. Run `com.qiujie.util.DataLoader` to load data into Redis (⚠️ Run this before starting the example for the first time)

3. There are some examples in the `com.qiujie.example` package.
In addition, it is recommended to adjust the heap memory of the startup program to at least 512MB










