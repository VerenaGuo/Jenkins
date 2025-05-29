pipeline {
    agent any
    // 定义环境变量
    environment {
        // 需要构建的分支名称
        branch = "qa"
        // 项目名称
        Project_Name = "velo-android"
        // Apache服务器Android项目根目录
        webBasePath = "/Library/WebServer/Documents/android/"
        // 项目存储地址
        buildPath = "/Users/yuguo/.jenkins/workspace/Business-Android/velo-android/"
    }
    stages {
        stage("Initial") {
            steps {
                script {
                    // 初始化日期, 日期可以作为每一次构建的唯一标识
                    def currentDate = new Date()
                    def formattedDate = currentDate.format("yyyyMMdd_HHmmss")
                    env.date = formattedDate
                    // 判断项目目录是否存在
                    if (fileExists("velo-android")) {
                        // 项目已经存在直接执行 git pull 更新代码.中国需要翻墙，设置代理
                        dir("velo-android") {
                            sh "export https_proxy=http://127.0.0.1:7897 http_proxy=http://127.0.0.1:7897 all_proxy=socks5://127.0.0.1:7897; git checkout ${env.branch}; git pull; git stash; git submodule update --init; git submodule update --remote"
                        }
                    } else {
                        // 项目不存在, 克隆指定的分支到本地.中国需要翻墙，设置代理
                        sh "export https_proxy=http://127.0.0.1:7897 http_proxy=http://127.0.0.1:7897 all_proxy=socks5://127.0.0.1:7897; git clone --branch ${env.branch} https://github.com/EastWestBank/velo-android.git"
                        dir("velo-android") {
                            sh "export https_proxy=http://127.0.0.1:7897 http_proxy=http://127.0.0.1:7897 all_proxy=socks5://127.0.0.1:7897; git submodule update --init; git submodule update --remote"
                        }
                    }
                 }
                dir("velo-android") {
                    echo "Update the config"
                    sh "cp -f /Users/yuguo/Documents/androidconfigjenkins/build1.gradle /Users/yuguo/.jenkins/workspace/Business-Android/velo-android/build.gradle; cp -f /Users/yuguo/Documents/androidconfigjenkins/gradle1.properties  /Users/yuguo/.jenkins/workspace/Business-Android/velo-android/gradle.properties;cp -f /Users/yuguo/Documents/androidconfigjenkins/build_mock.gradle /Users/yuguo/.jenkins/workspace/Business-Android/velo-android/velo-adobe/vladobe/build.gradle; cp -f /Users/yuguo/Documents/androidconfigjenkins/network_security_config1.xml  /Users/yuguo/.jenkins/workspace/Business-Android/velo-android/app/src/main/res-main/xml/network_security_config.xml"
                }
            }
        }
        stage("Build") {
            steps {
                dir("velo-android") {
                    // 清理项目
                    sh "./gradlew clean"
                }
            }
        }
        stage("Deploy") {
            steps {
                dir("velo-android") {
                    // 编译项目
                    sh "./gradlew clean assembleSMBQaDebug"
                    // 复制 apk 包到Apache服务器
                    sh "mkdir -p ${env.webBasePath}${env.Project_Name}/${env.date}"
                    sh "cp -f ${env.buildPath}app/build/outputs/apk/SMBqa/debug/app-SMBqa-debug.apk ${env.webBasePath}${env.Project_Name}/${env.date}/app-SMBqa-debug.apk"
                    // 生成二维码
                    sh "cd ${env.webBasePath}${env.Project_Name}/${env.date};/usr/local/bin/qrencode -o qrcode.png -s 4 -m 2 'http://192.168.8.56/android/${env.Project_Name}/${env.date}/app-SMBqa-debug.apk'"
                }
            }
        }
        stage ('Send Email with QR Code') {
            steps {
                script {
                    def imagePath = "${env.webBasePath}${env.Project_Name}/${env.date}/qrcode.png"
                    if (fileExists(imagePath)) {
                        def imageBytes = new File(imagePath).getBytes()
                        def imageBase64 = imageBytes.encodeBase64().toString()

                        mail(
                            subject: 'Business QA-Android',
                            body: """
                            <html><body>
                            <p>扫描二维码安装Business QA-Android应用</p>
                            <img src='data:image/png;base64,${imageBase64}' alt='qrcode' />
                            </body></html>
                            """,
                            from: "273254327@qq.com",
                            to: "yuping.guo@ftsynergy.com,chen.zhang@ftsynergy.com,bin.xing@ftsynergy.com,haolei.guo@ftsynergy.com,xingle.li@ftsynergy.com,wei.mao@ftsynergy.com,jizhi.qian@ftsynergy.com,jiajia.liu@ftsynergy.com",
                            mimeType: 'text/html'
                        )
                    } else {
                        error("二维码路径不存在：${imagePath}")
                    }
                }
            }
        }
    }
    
    post {
        success {
            script {
            // 使用优化后的 HTML 代码设置构建描述
                currentBuild.description = """
                <div style="padding: 10px; background-color: #000; border: 1px solid #333; color: #fff;">
                    <h3>Business QA-Android</h3>
                    <a href='http://192.168.8.56/android/${env.Project_Name}/${env.date}/app-SMBqa-debug.apk' 
                    style='display: inline-block; padding: 8px 16px; background-color: #4CAF50; color: white; 
                            text-decoration: none; border-radius: 4px; border: 2px solid #45a049; 
                            box-shadow: 0 2px 4px rgba(0,0,0,0.2); transition: all 0.2s;'>
                        下载 app-SMBqa-debug.apk
                    </a>
                    <p style="margin-top: 10px; color: #ccc;">或者扫描二维码安装：</p>
                    <img src='http://192.168.8.56/android/${env.Project_Name}/${env.date}/qrcode.png' 
                        alt='qrcode' 
                        style='max-width: 200px; border: 4px solid #222; border-radius: 8px; 
                                background-color: #111; padding: 5px; margin-top: 10px; 
                                box-shadow: 0 0 10px rgba(76, 175, 80, 0.2);'>
                </div>
                """
            }

            // 把最新包的二维码自动更新到localhost页面上去
            sh "cp -f ${env.webBasePath}${env.Project_Name}/${env.date}/qrcode.png /Library/WebServer/Documents/qrcodeandroid_business.png"
        
            // 删除无效缓存，列出所有文件，按字典顺序排序，保留前5个，删除其余(保留最新的5次构建)
            sh "cd ${env.webBasePath}${env.Project_Name};ls -1 | sort -r | tail -n +6 | xargs -I {} rm -rf {}"
        }
    }
}