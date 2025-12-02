#!/usr/bin/bash

dir="./"
#备份变量“$IFS”；因为文件名有空格
SAVEIFS=$IFS
IFS=$(echo -en "\n\b")

##创建全集文件夹
folderName="galary"
if [ ! -d ${folderName} ];then      
   mkdir ${folderName}
fi
##遍历文件夹“${dir}”，修改些文件夹下的文件夹内文件名称
for df in `ls ${dir}`
do
   tmp1=${df}
##获取“${dir}”下的子文件夹名称，然后将子文件名称中的“空格”以“-”代替，然后将子文件夹内的文件前缀加上子文件夹名称
  if [[ -d ${tmp1} && ${tmp1} != "${folderName}" ]];then
#子文件夹名称，即子文件夹内文件名称的添加的前缀
      tmp2=`echo ${tmp1} | sed 's/ /-/g'`   
  
##遍历子文件夹，将子文件夹文件名称加上前缀
      for fileslist in `ls ${tmp1}`
      do
         filesList=${fileslist}
#将子文件夹内文件名称，“-”至开始的字符删除；目的，是为了重复运行该脚本，因为每运行一次，前缀增加一次
         filesList=${filesList##*-}
#         echo "${dir}${tmp1}/${fileslist}"
         oldName="${dir}${tmp1}/${fileslist}"
         newName="${dir}${tmp1}/${tmp2}-${filesList}"
#         echo $newName
         if [ ${oldName} != ${newName} ];then
            mv ${oldName} ${newName}
         fi
         mv ${newName} ${folderName}
#        if [ ! "$(ls -A ${fileslist})" ];then
#            rmdir ${fileslist}
#        fi
#         if [ $? -eq 0 ];then
#             echo $newName
        
#            mv ${newName} ${folderName}
#         fi
      done
      if [ ! "$(ls -A ${df})"  ];then
         rmdir ${df}
      fi
   fi
done
IFS=$SAVEIFS
