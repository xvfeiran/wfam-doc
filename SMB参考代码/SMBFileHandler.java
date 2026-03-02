package com.bosch.cn.em.mfd.core.util;

import com.bosch.cn.em.mfd.core.base.exception.BaseException;
import com.bosch.cn.em.mfd.core.base.exception.ExceptionEnum;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mserref.NtStatus;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "custom.smb.enabled", havingValue = "true", matchIfMissing = false)
public class SMBFileHandler {

    private final GenericObjectPool<DiskShare> diskSharePool;

    @Value("${custom.smb.env}")
    private String environment;

    @Value("${custom.smb.prefix}")
    private String prefix;

    public void createDirectory(String dirPath) {
        DiskShare diskShare = null;
        try {
            diskShare = diskSharePool.borrowObject();
            innerCreateDirectory(diskShare, dirPath);
        } catch (Exception e) {
            throw BaseException.builder()
                    .exceptionEnum(ExceptionEnum.FAILED_TO_GET_SMB_CONNECTION)
                    .build();
        } finally {
            if (diskShare != null) {
                diskSharePool.returnObject(diskShare);
            }
        }
    }

    private void innerCreateDirectory(DiskShare diskShare, String dirPath) {
        dirPath = prefix + java.io.File.separator + environment + java.io.File.separator + dirPath;
        if (!diskShare.folderExists(dirPath)) {
            diskShare.mkdir(dirPath);
        }
    }

    public void writeFile(String filePath, MultipartFile file) {
        DiskShare diskShare = null;
        try {
            diskShare = diskSharePool.borrowObject();
            innerWriteFile(diskShare, filePath, file);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw BaseException.builder()
                    .exceptionEnum(ExceptionEnum.FAILED_TO_GET_SMB_CONNECTION)
                    .build();
        } finally {
            if (diskShare != null) {
                diskSharePool.returnObject(diskShare);
            }
        }
    }

    private void innerWriteFile(DiskShare diskShare, String filePath, MultipartFile file) throws IOException {
        filePath = prefix + java.io.File.separator + environment + java.io.File.separator + filePath;
        try (File smbFile = diskShare.openFile(filePath, EnumSet.of(AccessMask.GENERIC_WRITE), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_CREATE, null)) {
            try (OutputStream os = smbFile.getOutputStream();
                 InputStream is = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
        } catch (SMBApiException e) {
            long statusCode = e.getStatusCode();
            if (statusCode == NtStatus.STATUS_OBJECT_NAME_COLLISION.getValue()) {
                log.warn("File {} already exists.", filePath);
            } else {
                log.error(e.getMessage());
                throw BaseException.builder()
                        .exceptionEnum(ExceptionEnum.FAILED_TO_WRITE_FILE)
                        .build();
            }
        }
    }

    public ByteArrayResource readFile(String filePath) {
        DiskShare diskShare = null;
        try {
            diskShare = diskSharePool.borrowObject();
            return innerReadFile(diskShare, filePath);
        } catch (IOException e) {
            throw BaseException.builder()
                    .exceptionEnum(ExceptionEnum.FAILED_TO_READ_FILE)
                    .build();
        } catch (Exception e) {
            throw BaseException.builder()
                    .exceptionEnum(ExceptionEnum.FAILED_TO_GET_SMB_CONNECTION)
                    .build();
        } finally {
            if (diskShare != null) {
                diskSharePool.returnObject(diskShare);
            }
        }
    }

    private ByteArrayResource  innerReadFile(DiskShare diskShare, String filePath) throws IOException {
        filePath = prefix + java.io.File.separator + environment + java.io.File.separator + filePath;
        try (File file = diskShare.openFile(filePath, EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null);
             InputStream inputStream = file.getInputStream();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            return new ByteArrayResource(outputStream.toByteArray());
        }
    }

    private List<FileIdBothDirectoryInformation> innerListDirectory(DiskShare diskShare, String dirPath) { // NOSONAR
        return diskShare.list(dirPath);
    }
}
 