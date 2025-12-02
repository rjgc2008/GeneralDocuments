# !/bin/bash
#批量重命令不合法的文件名
function renameFiles()
{
    category=$1 #传入的参数，生效的只有2种,"f"或者"d"
    echo "${category}'s cycle: current path is $PWD"
    # FILEPATH="/media/pi/linux/200.SELF/1"  #目标路径，也即搜索路径
    # FILEPATH="/media/pi/影视/tmp3"  #目标路径，也即搜索路径
    # FILEPATH="/media/pi/影视/tmp2"  #目标路径，也即搜索路径
    FILEPATH="/media/pi/影视/迅雷下载"  #目标路径，也即搜索路径
    #优先对文件夹进行处理，通常只有3层，可以调整
    for i in $(seq 1 10); do
        echo "find $FILEPATH -mindepth $i -maxdepth $i -type $category -print0"
        # files=$(find $FILEPATH -mindepth $i -maxdepth $i -type d)  #产生的是一个字条串而不是一个数组
        filesArray=()
        while IFS= read -r -d $'\0' line ; do #语法就是很固定，自己尝试加"``" "[]" "[[]]"都不行，很奇怪
            filesArray+=("$line")
            echo "${i}'s cycle : the connent of add to var: $line"
        done < <(find $FILEPATH -mindepth $i -maxdepth $i -type $category -print0) #这里的语法也很奇怪"< <"之间必须有空格

        ##下面这部分不想被执行，仅供参考和学习遍历数组的语法 
        if false ; then
            ##循环遍历1
            for i in "${!filesArray[@]}";
            do
                printf "first way:%s\n" "${filesArray[$i]}"
                echo "first way1: ${filesArray[$i]}"
            done
            ##循环遍历2,不是很有效，字符串中有空格则失效
            for i in ${filesArray[@]}
            do
                echo "second way: $i"
            done
            #循环遍历3
            for(( i=0;i<${#filesArray[@]};i++ )) do
                echo "third way: ${filesArray[i]}"
            done
        fi

        ##过滤并修改目录名
        for i in "${!filesArray[@]}";
        do
            filePath=${filesArray[$i]} #文件路径
            # echo "search path: $filePath"
            DirName=$(echo "$filePath" | awk -F '/' '{printf($NF)}')  #文件名
            UpperDirName=$(echo "$filePath" | awk -F '/' '{printf($(NF-1))}') #上层目录名称
            UpperPathName=$(echo "$filePath" | sed "s/$DirName//g") #上层目录路径

            if false; then
            fileName=${filePath##/*/}  #获取文件名称的简易写法
                UpperDirPath=${filePath%/*}   #获取文件上层目录路径的简易写法
                echo "---"
                echo "filename : $fileName   UpperDirPath : $UpperDirPath"
                echo "---"
                echo "Dir name: $DirName"
                echo "Upper Dir name: $UpperDirName"
                echo "UpperPath : $UpperPathName"
            fi

            case $DirName in
                *\ * )
                    rename -v 's/ //g' "$filePath"
                    ;;
                *，* )
                    rename -v 's/，//g' "$filePath"
                    ;;
                *！* )
                    rename -v 's/！//g' "$filePath"
                    ;;
                *,* )
                    rename -v 's/,//g' "$filePath"
                    ;;
                *＊* )
                    rename -v 's/＊//g' "$filePath"
                    ;;
                *～* )
                    rename -v 's/～//g' "$filePath"
                    ;;
                原创文宣 )
                    rm -v -fr "$filePath"
                    ;;
                *.mht )
                    rm -v "$filePath"
                   ;;
                *1024.jpg )
                    rm -v "$filePath"
                   ;;
                *1024核工厂*.jpg )
                    rm -v "$filePath"
                   ;;
                *xp1024.com.jpg )
                    rm -v "$filePath"
                   ;;
                *jwdfj.jpg )
                    rm -v "$filePath"
                   ;;                      
                *padding_file* )
                    rm -v "$filePath"
                   ;;   
                *.chm )
                    rm -v "$filePath"
                   ;;   
                *.htm )
                    rm -v "$filePath"
                   ;;   
                *.html )
                    rm -v "$filePath"
                   ;;
                *.url )
                    rm -v "$filePath"
                   ;;   
                UUE29.mp4 )
                    rm -v "$filePath"
                   ;;   
                *安卓二维码*.png )        
                    rm -v "$filePath"
                   ;;   
                *扫码获取最新地址*.png )        
                    rm -v "$filePath"
                   ;;   
                *最新地址获取*.txt )        
                    rm -v "$filePath"
                   ;;   
                *最新地址*.png )        
                    rm -v "$filePath"
                   ;;   
                uuv79.mp4 )        
                    rm -v "$filePath"
                   ;;   
                *日更新.TXT )        
                    rm -v "$filePath"
                   ;;   
                *二维码*.png )        
                    rm -v "$filePath"
                   ;;   
                *.torrent )        
                    rm -v "$filePath"
                   ;;   
                *第一会所*.jpg )        
                    rm -v "$filePath"
                   ;;   
                *联盟.gif )        
                    rm -v "$filePath"
                   ;;   
                *安卓*二维码*.jpg )        
                    rm -v "$filePath"
                   ;;   
                *最新地址*.txt )        
                    rm -v "$filePath"
                   ;;   
                *春暖花开*.jpg )        
                    rm -v "$filePath"
                   ;;   
                *1024.jpg )        
                    rm -v "$filePath"
                   ;;   
                *伊甸园*.jpg )        
                    rm -v "$filePath"
                   ;;   
                *王之家*.rar )        
                    rm -v "$filePath"
                   ;;  
                *.lnk )        
                    rm -v "$filePath"
                   ;;  
                *色中色*.txt )        
                    rm -v "$filePath"
                   ;; 
                *.URL )        
                    rm -v "$filePath"
                   ;;
                *封杀SIS001*.jpg )        
                    rm -v "$filePath"
                   ;; 
                *Board.png )        
                    rm -v "$filePath"
                   ;;
                xssep.png )        
                    rm -v "$filePath"
                   ;;    
                *宣传图*.jpg )        
                    rm -v "$filePath"
                   ;; 
                *草榴社区*.jpg )        
                    rm -v "$filePath"
                   ;;  
                *杏吧*.txt )        
                    rm -v "$filePath"
                   ;;  
                *美女裸聊*.mp4 )        
                    rm -v "$filePath"
                   ;;
                *荷官*.mp4 )        
                    rm -v "$filePath"
                   ;;
                *翌闌扦*.jpg )        
                    rm -v "$filePath"
                   ;; 
                *杏吧社区*.jpg )        
                    rm -v "$filePath"
                   ;;     
            esac
        done
    done
    }


renameFiles "d" #对文件夹进行过滤修改
renameFiles "f" #对文件进行过滤 修改