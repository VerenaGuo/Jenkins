pipeline {
    agent any
    // 定义环境变量
    environment {
        // 需要构建的分支名称
        branch = "uat"
        // 项目名称
        Project_Name = "velo-ios"
        // Target类型
        Confignation_Target = "Release"
        // Apache服务器iOS项目根目录
        webBasePath = "/Library/WebServer/Documents/ios/"
        // 项目存储地址
        buildPath = "/Users/yuguo/.jenkins/workspace/UAT-iOS/velo-ios"
        // plist路径
        AdHocExportOptionsPlist = "/Users/yuguo/App/ipa/ExportOptions.plist"
        // archive存储路径
        archivePath = "/Users/yuguo/Library/Developer/Xcode/Archives/velo-ios.xcarchive"
        // ipa存储路径
        ipaPath = "/Users/yuguo/App/ipa"
        PROXY_SETTINGS = "export https_proxy=http://127.0.0.1:7897 http_proxy=http://127.0.0.1:7897 all_proxy=socks5://127.0.0.1:7897"
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
                    if (fileExists("velo-ios")) {
                        // 项目已经存在直接执行 git pull 更新代码.在中国需要翻墙设代理
                        dir("velo-ios") {
                            sh "${env.PROXY_SETTINGS};git stash; git checkout ${env.branch}; git pull origin ${env.branch}"
                        }
                    } else {
                        // 项目不存在, 克隆指定的分支到本地.在中国需要翻墙设代理
                        sh "${env.PROXY_SETTINGS}; git clone --branch ${env.branch} https://github.com/EastWestBank/velo-ios.git"
                    }
                }
                dir("velo-ios") {
                    // 初始化配置
                    echo "Reset config"
                    sh "git reset --hard HEAD"
                    // 执行 pod install 更新项目中的第三方库
                    sh "cp -f /Users/yuguo/Documents/iosconfigjenkis/Podfile /Users/yuguo/.jenkins/workspace/UAT-iOS/velo-ios"
                    sh "${env.PROXY_SETTINGS};export LANG=en_US.UTF-8;/usr/local/bin/pod cache clean --all;/usr/local/bin/pod deintegrate;/usr/local/bin/pod install"
                    // 更新配置
                    sh "cp -f /Users/yuguo/Documents/iosconfigjenkis/VLAppConfiguration.plist /Users/yuguo/.jenkins/workspace/UAT-iOS/velo-ios/velo-ios/Configurations/VLAppConfiguration.plist"
                    sh "cp -f /Users/yuguo/Documents/iosconfigjenkis/velo-iosDebug.entitlements /Users/yuguo/.jenkins/workspace/UAT-iOS/velo-ios/velo-ios/velo-iosDebug.entitlements"
                    sh "cp -f /Users/yuguo/Documents/iosconfigjenkis/velo-iosRelease.entitlements /Users/yuguo/.jenkins/workspace/UAT-iOS/velo-ios/velo-ios/velo-iosRelease.entitlements"
                    sh "cp -f /Users/yuguo/Documents/iosconfigjenkis/velo-ios.xcodeproj/project.pbxproj /Users/yuguo/.jenkins/workspace/UAT-iOS/velo-ios/velo-ios.xcodeproj/project.pbxproj"
                }
            }
        }
        stage("Build") {
            steps {
                dir("velo-ios") {
                    // 清理项目
                    sh "xcodebuild clean -workspace ${env.Project_Name}.xcworkspace -scheme ${env.Project_Name} -configuration ${env.Confignation_Target}"
                    // 编译项目
                    sh "xcodebuild archive -workspace ${env.Project_Name}.xcworkspace -scheme ${env.Project_Name} -sdk iphoneos -archivePath ${env.archivePath} -configuration ${env.Confignation_Target}"
                }
            }
        }
        stage("Deploy") {
            steps {
                dir("velo-ios") {
                    // 导出 ipa 包
                    sh "xcodebuild -exportArchive -archivePath ${env.archivePath} -exportPath ${env.ipaPath} -exportOptionsPlist ${env.AdHocExportOptionsPlist}"
                    // 复制 ipa 包到Apache服务器
                    sh "mkdir -p ${env.webBasePath}${env.Project_Name}/${env.date}"
                    sh "cp -f /Users/yuguo/App/ipa/velo-ios.ipa ${env.webBasePath}${env.Project_Name}/${env.date}/velo-ios.ipa"
                }
                // 复制 plist 模版
                sh "cd /Library/WebServer/Documents/ios;cp -f ExportOptions.plist ${env.Project_Name}/${env.date}/ExportOptions.plist"
                // 修改 plist
                script {
                    // 定义文件路径
                    def filePath = "${env.webBasePath}${env.Project_Name}/${env.date}/ExportOptions.plist"
                    // 读取文件内容
                    def fileContent = readFile(filePath)
                    // 替换字符串
                    def updatedContent = fileContent.replaceAll("http://192.168.8.56/ios/velo-ios.ipa", "http://192.168.8.56/ios/${env.Project_Name}/${env.date}/velo-ios.ipa")
                    // 将更新后的内容写回文件
                    writeFile(file: filePath, text: updatedContent)
                }
                // 生成二维码
                sh "cd ${env.webBasePath}${env.Project_Name}/${env.date};/usr/local/bin/qrencode -o qrcode.png -s 4 -m 2 \"itms-services://?action=download-manifest&url=https://192.168.8.56/ios/${env.Project_Name}/${env.date}/ExportOptions.plist\""
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
                            subject: 'UAT-iOS Velo',
                            body: """
                            <html><body>
                            <p>扫描二维码安装UAT-iOS应用</p>
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
                // 添加下载链接到构建描述
                currentBuild.description = """
                <div style="padding: 10px; background-color: #000; border: 1px solid #333; color: #fff;">
                    <h3>UAT iOS Velo 下载</h3>
                    <a href='http://192.168.8.56/ios/${env.Project_Name}/${env.date}/velo-ios.ipa' style='display: inline-block; padding: 8px 16px; background-color: #4CAF50; color: white; text-decoration: none; border-radius: 4px;'>
                        下载 velo-ios.ipa
                    </a>
                    <p style="margin-top: 10px;">或者扫描二维码安装：</p>
                    <img src='http://192.168.8.56/ios/${env.Project_Name}/${env.date}/qrcode.png' alt='qrcode' style='max-width: 200px;'>
                </div>
                """
            }
            // 更新index.html页面的二维码
            sh "cp -f ${env.webBasePath}${env.Project_Name}/${env.date}/qrcode.png /Library/WebServer/Documents/qrcodeios_uat.png"
            // 删除无效缓存，列出所有文件，按字典顺序排序，保留前5个，删除其余(保留最新的5次构建)
            sh "cd ${env.webBasePath}${env.Project_Name};ls -1 | sort -r | tail -n +6 | xargs -I {} rm -rf {}"
        }
    }
}