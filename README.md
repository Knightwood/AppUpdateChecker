<p align="center">
  <img src="https://img.shields.io/badge/miniSdk-16%2B-blue.svg">
  <img src="https://img.shields.io/badge/license-Apache2.0-orange.svg">
</p>

## 效果图

<img src="img/zh/zh_1.png" width="300"><img src="img/zh/zh_2.png" width="300">
<img src="img/zh/zh_3.png" width="300"><img src="img/zh/zh_4.png" width="300">
<img src="img/zh/zh_5.png" width="300"><img src="img/zh/zh_6.png" width="300">
<img src="img/zh/zh_7.png" width="300">

## demo中的自定义下载弹窗示例

<img src="img/zh/zh_8.jpg" width="300"><img src="img/zh/zh_9.jpg" width="300">

### 功能介绍

* [x] 支持Kotlin
* [x] 界面定制变得极为简单
* [x] 支持后台下载
* [x] 支持强制更新
* [x] 支持自定义下载过程
* [x] 支持Android4.1及以上版本
* [x] 支持通知栏进度条展示(或者自定义显示进度)
* [x] 支持中文/繁体/英文语言（国际化）
* [x] 支持取消下载(如果发送了通知栏消息，则会移除)
* [x] 支持下载完成 打开新版本后删除旧安装包文件
* [x] 不需要申请存储权限
* [x] 使用HttpURLConnection下载，未集成其他第三方框架

## 使用步骤



### 1.添加依赖

####  最新版本： [![](https://jitpack.io/v/Knightwood/AppUpdate2.svg)](https://jitpack.io/#Knightwood/AppUpdate2)

groovy:

```groovy
    implementation 'com.github.knightwood:appUpdate2:5.0.0'
```

kotlin:

```kotlin
    implementation("com.github.knightwood:appUpdate2:5.0.0")
```

settings.gradle可能需要添加:

```kotlin

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://www.jitpack.io")
    }
}
```

### 2.创建`DownloadManager`，显示更新界面。

### 更多用法请查看[这里示例代码](https://github.com/Knightwood/AppUpdate/blob/main/app/src/main/java/com/azhon/app/MainActivity.kt)

步骤：

1. 配置一个DownloadManager
2. 显示更新弹窗界面

示例:

```kotlin
//配置一个DownloadManager
val manager = DownloadManager.config(application) {
    //这里指定使用内置的不同更新界面，如果需要自己定制界面，指定为UpdateDialogType.None      
    updateDialogType = UpdateDialogType.Colorful
    apkUrl = url
    apkName = this@MainActivity.apkName
    apkVersionCode = 2
    apkVersionName = "v4.2.1"
    apkSize = "7.7MB"
    apkDescription = getString(R.string.dialog_msg)
    forcedUpgrade = false
}

//显示更新弹窗界面
val manager = downloadApp(manager!!)

```

`DownloadManager`是个单例，每次使用`DownloadManager.DownloadConfig`配置`DownloadManager`
时，都会取消上一个下载任务。

如果要判断当前没有没下载，可以调用如下方法进行判断

```kotlin
    DownloadManager.isDownloading()
```

* 内置界面

```kotlin
class UpdateDialogType {
    companion object {
        const val None = 0 //不使用界面
        const val Colorful = 1 //默认更新界面，由DialogFragment实现
        const val SimpleDialog = 2 //MaterialAlertDialog实现的更新界面
    }
}
```



### 配置下载信息得到DownloadManager详细用法

#### 使用DownloadManager.DownloadConfig配置DownloadManager

配置项中，必需的参数有`apkUrl`、`apkName`、`apkDescription`

- DownloadManager

| 函数和变量             | 描述       |
|-------------------|----------|
| canDownload       | 是否可以下载   |
| checkThenDownload | 检查并下载    |
| directDownload    | 直接下载     |
| downloadStateFlow | 下载任务的状态流 |
| downloadState     | 是否正在下载   |
| config            | 配置项      |

- AppUpgradeHolder
  用于根据manager显示更新界面，一些快捷方法，如果你自定义界面，就用不到他

| 函数                                                                     | 描述                   |
|------------------------------------------------------------------------|----------------------|
| Activity.downloadApp(downloadManager: DownloadManager)                 | 配置并显示内置下载界面          |
| Activity.downloadApp(config: DownloadManager.DownloadConfig)           | 配置并显示内置下载界面          |
| Activity.downloadApp(block: DownloadManager.DownloadConfig.() -> Unit) | 配置并显示内置下载界面          |
| DownloadManager.download(activity: Activity)                           | 使用Builder配置并显示内置下载界面 |
| DownloadManager.download()                                             | 使用Builder配置并显示内置下载界面 |
| showUi(downloadManager: DownloadManager, activity: Activity)           | 仅显示内置下载界面            |

对于`showUi`方法：在使用内置界面时，你开始下载应用，并关闭了下载弹窗。
当你调用showUi时，弹窗会继续显示下载进度，而不会重新开始下载。

* 也可以直接配置并显示更新界面

```
直接通过downloadApp方法构造downloadmanager并显示更新界面
val manager=downloadApp {
            //。。。。。
     		//跟上面一样的配置
        }
```

#### 使用Builder配置DownloadManager

```kotlin
val downloadManager = DownloadManager.Builder(this).run {
    apkUrl(url)
    apkVersionCode(2)
    apkVersionName("v4.2.1")
    apkSize("7.7MB")
    apkName(this@MainActivity.apkName)
    apkDescription(getString(R.string.dialog_msg))
    updateDialogType(UpdateDialogType.SimpleDialog)//修改样式
    build()
}
downloadManager.download(activity)//如果不传activity参数，updateDialogType则不起作用

```

### 自定义界面

##### 自定义界面
- 需要做的事情极其简单，你不需要关注下载的流程，监听之类的，只需要继承并重写布局，然后显示这个自定义的DialogFragment

* 步骤：
1. 写一个类继承`BaseUpdateDialogFragment`，重写布局
2. 跟往常一样通过`DownloadManager.config(application)`生成一个[DownloadManager]
3. 显示你的自定义的DialogFragment

注意，在BaseUpdateDialogFragment中，进度条可以是任意视图，如果你的进度条不是`ProgressBar`，那么你需要重写

`initProgressBar`和`setProgress`方法。
比如：
```kotlin
/**
 * 设置进度条进度
 * @param progressValue Int
 */
override fun setProgress(progressValue: Int) {
  (progressBar() as NumberProgressBar).progress = progressValue
}

/**
 * 初始化进度条
 *
 */
override fun initProgressBar() {
  (progressBar() as NumberProgressBar).max = 100
}

```

自定义界面示例：

1. 继承BaseUpdateDialogFragment

```kotlin
class Win8UpdateDialogFragment : BaseUpdateDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setStyle(STYLE_NORMAL, R1.style.M3AppTheme)
        mView = inflater.inflate(R.layout.app_update_dialog_win8, container, false)
        //全屏
        dialog?.window?.let { window ->
            //这步是必须的
            window.setBackgroundDrawableResource(R1.color.transparent)
            //必要，设置 padding，这一步也是必须的，内容不能填充全部宽度和高度
            window.decorView.setPadding(0, 0, 0, 0)
            // 关键是这句，其实跟xml里的配置长得几乎一样
            val wlp: WindowManager.LayoutParams = window.attributes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                wlp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            wlp.gravity = Gravity.CENTER
            wlp.width = WindowManager.LayoutParams.MATCH_PARENT
            wlp.height = WindowManager.LayoutParams.WRAP_CONTENT
            window.attributes = wlp
        }
        return mView
    }


    companion object {
        const val TAG = "Win8UpdateDialogFragment"

        fun open(host: FragmentActivity) {
            host.run {
                val dialog = Win8UpdateDialogFragment()
                val ft = supportFragmentManager.beginTransaction()
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                dialog.show(ft, TAG)
            }
        }
    }

}
```

2. 展示界面

```kotlin
//跟往常一样构造DownloadManager
manager = DownloadManager.config(application) {
    //...一些配置项
}

//直接显示界面即可，不需要关注除了界面之外的任何内容
Win8UpdateDialogFragment.open(this)
```

详情查看app中的`PixelUpdateDialogFragment`和`Win8UpdateDialogFragment`

DownloadManager的一些其他配置项

```kotlin
 val manager = DownloadManager.config(application) {
    updateDialogType = UpdateDialogType.None //不使用内置界面
    registerDownloadListener(listenerAdapter)//自行监听下载进度
    httpManager = myHttpManager //替换默认下载功能
    notificationChannel = ... //修改通知渠道
    onButtonClickListener = {} //监听界面的按钮事件（BaseUpdateDialogFragment）
}
manager.checkThenDownload()//立即开始下载

```

* 自定义下载时，需继承`BaseHttpDownloadManager`

## 混淆打包，只需保持`Activity`、`Service`不混淆

```groovy
-keep public class * extends android.app.Activity
- keep public class * extends android.app.Service
```

### 使用技巧

* 框架内部支持国际化（其他语言只需要在对应的`string.xml`中取相同的名字即可）
* 如果你需要修改框架内部的一些文字，你只需要在`string.xml`中取相同的名字即可以覆盖框架内设定的
* 查看版本库中的Log只需要过滤`AppUpdate`开头的Tag
* 支持校验安装包的MD5避免重复下载，只需要`Builder`设置安装包的MD5即可
* 下载完成 打开新版本后删除旧安装包文件
* 当设置apkVersionCode为MAX_VALUE时，将不校验版本号

- 适配Android 13通知运行权限，且当设置`showNotification(true)`时，点击对话框的升级按钮会申请通知栏权限，无论是否同意都将会继续下载
- 当设置强制更新`forcedUpgrade(true)`时，显示的对话框会显示下载进度条
- 由于Android 10限制后台应用启动Activity，所以下载完成会发送一个通知至通知栏（忽略showNotification的值，需要允许发送通知）

### 相关文档链接

- [限制后台启动Activity](https://developer.android.google.cn/guide/components/activities/background-starts)
- [通知栏适配](https://developer.android.google.cn/guide/topics/ui/notifiers/notifications?hl=zh-cn)

### 删除旧安装包

现在，下载的安装包会放入 /sdcard/Android/data/包名/cache/AppUpdateCache 下
而不会放在/sdcard/Android/data/包名/cache 下

```kotlin
//删除某一个文件
val result =
    ApkUtil.deleteOldApk(this, "${externalCacheDir?.path}/${Constant.cacheDirName}/$apkName")

//删除所有下载的文件
ApkUtil.deleteDefaultCacheDir(application)
```