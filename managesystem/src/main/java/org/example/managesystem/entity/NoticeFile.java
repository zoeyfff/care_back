package org.example.managesystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notice_file")
public class NoticeFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long noticeId;
    private String fileName;
    private String fileExt;
    private Long fileSize;
    private String contentType;
    private String storageType;
    private String fileKey;
    private String fileHash;
    private Long uploaderId;
    private String uploaderName;
    private LocalDateTime createTime;
    private Integer isDeleted;
}

