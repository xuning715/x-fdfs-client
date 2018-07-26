package com.github.tobato.fastdfs.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Component;

import com.github.tobato.fastdfs.domain.MateData;
import com.github.tobato.fastdfs.domain.StorageNode;
import com.github.tobato.fastdfs.domain.StorePath;
import com.github.tobato.fastdfs.domain.ThumbImageConfig;
import com.github.tobato.fastdfs.exception.FdfsUnsupportImageTypeException;
import com.github.tobato.fastdfs.exception.FdfsUploadImageException;
import com.github.tobato.fastdfs.proto.storage.StorageSetMetadataCommand;
import com.github.tobato.fastdfs.proto.storage.StorageUploadFileCommand;
import com.github.tobato.fastdfs.proto.storage.StorageUploadSlaveFileCommand;
import com.github.tobato.fastdfs.proto.storage.enums.StorageMetdataSetType;

import net.coobird.thumbnailator.Thumbnails;

/**
 * 面向应用的接口实现
 * 
 * @author tobato
 *
 */
@Component
public class DefaultFastFileStorageClient extends DefaultGenerateStorageClient implements FastFileStorageClient {

    /** 支持的图片类型 */
    private static final String[] SUPPORT_IMAGE_TYPE = { "JPG", "JPEG", "PNG", "GIF", "BMP", "WBMP" };
    private static final List<String> SUPPORT_IMAGE_LIST = Arrays.asList(SUPPORT_IMAGE_TYPE);
    /** 缩略图生成配置 */
    @Resource
    private ThumbImageConfig thumbImageConfig;

    /**
     * 上传文件
     */
    @Override
    public StorePath uploadFile(InputStream inputStream, long fileSize, String fileExtName, Set<MateData> metaDataSet) {
        Validate.notNull(inputStream, "上传文件流不能为空！");
        Validate.notBlank(fileExtName, "文件扩展名不能为空！");
        StorageNode client = trackerClient.getStoreStorage();
        return uploadFileAndMateData(client, inputStream, fileSize, fileExtName, metaDataSet);
    }

    /**
     * 上传图片并且生成缩略图
     */
    @Override
    public StorePath uploadImageAndCrtThumbImage(InputStream inputStream, long fileSize, String fileExtName,
            Set<MateData> metaDataSet) {
        Validate.notNull(inputStream, "上传文件流不能为空");
        Validate.notBlank(fileExtName, "文件扩展名不能为空");
        // 检查是否能处理此类图片
        if (!isSupportImage(fileExtName)) {
            throw new FdfsUnsupportImageTypeException("不支持的图片格式" + fileExtName);
        }
        StorageNode client = trackerClient.getStoreStorage();
        byte[] bytes = inputStreamToByte(inputStream);

        // 上传文件和mateData
        StorePath path = uploadFileAndMateData(client, new ByteArrayInputStream(bytes), fileSize, fileExtName,
                metaDataSet);
        // 上传缩略图
        uploadThumbImage(client, new ByteArrayInputStream(bytes), path.getPath(), fileExtName);
        bytes = null;
        return path;
    }

    /**
     * 获取byte流
     * 
     * @param inputStream
     * @return
     */
    private byte[] inputStreamToByte(InputStream inputStream) {
        try {
            return IOUtils.toByteArray(inputStream);
        } catch (IOException e) {
            LOGGER.error("image inputStream to byte error", e);
            throw new FdfsUploadImageException("upload ThumbImage error", e.getCause());
        }
    }

    /**
     * 检查是否有MateData
     * 
     * @param metaDataSet
     * @return
     */
    private boolean hasMateData(Set<MateData> metaDataSet) {
        return null != metaDataSet && !metaDataSet.isEmpty();
    }

    /**
     * 是否是支持的图片文件
     * 
     * @param fileExtName
     * @return
     */
    private boolean isSupportImage(String fileExtName) {
        return SUPPORT_IMAGE_LIST.contains(fileExtName.toUpperCase());
    }

    /**
     * 上传文件和元数据
     * 
     * @param client
     * @param inputStream
     * @param fileSize
     * @param fileExtName
     * @param metaDataSet
     * @return
     */
    private StorePath uploadFileAndMateData(StorageNode client, InputStream inputStream, long fileSize,
            String fileExtName, Set<MateData> metaDataSet) {
        // 上传文件
        StorageUploadFileCommand command = new StorageUploadFileCommand(client.getStoreIndex(), inputStream,
                fileExtName, fileSize, false);
        StorePath path = connectionManager.executeFdfsCmd(client.getInetSocketAddress(), command);
        // 上传matadata
        if (hasMateData(metaDataSet)) {
            StorageSetMetadataCommand setMDCommand = new StorageSetMetadataCommand(path.getGroup(), path.getPath(),
                    metaDataSet, StorageMetdataSetType.STORAGE_SET_METADATA_FLAG_OVERWRITE);
            connectionManager.executeFdfsCmd(client.getInetSocketAddress(), setMDCommand);
        }
        return path;
    }

    /**
     * 上传缩略图
     * 
     * @param client
     * @param inputStream
     * @param fileSize
     * @param fileExtName
     * @param metaDataSet
     */
    private void uploadThumbImage(StorageNode client, InputStream inputStream, String masterFilename,
            String fileExtName) {
        ByteArrayInputStream thumbImageStream = null;
        try {
            thumbImageStream = getThumbImageStream(inputStream);// getFileInputStream
            // 获取文件大小
            long fileSize = thumbImageStream.available();
            // 获取缩略图前缀
            String prefixName = thumbImageConfig.getPrefixName();
            StorageUploadSlaveFileCommand command = new StorageUploadSlaveFileCommand(thumbImageStream, fileSize,
                    masterFilename, prefixName, fileExtName);
            connectionManager.executeFdfsCmd(client.getInetSocketAddress(), command);

        } catch (IOException e) {
            LOGGER.error("upload ThumbImage error", e);
            throw new FdfsUploadImageException("upload ThumbImage error", e.getCause());
        } finally {
            IOUtils.closeQuietly(thumbImageStream);
        }
    }

    /**
     * 获取缩略图
     * 
     * @param filePath
     * @return
     * @throws IOException
     */
    private ByteArrayInputStream getThumbImageStream(InputStream inputStream) throws IOException {
        // 在内存当中生成缩略图
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        //@formatter:off
        Thumbnails
          .of(inputStream)
          .size(thumbImageConfig.getWidth(), thumbImageConfig.getHeight())
          .toOutputStream(out);
        //@formatter:on
        return new ByteArrayInputStream(out.toByteArray());
    }

    /**
     * 删除文件
     */
    @Override
    public void deleteFile(String filePath) {
        StorePath storePath = StorePath.praseFromUrl(filePath);
        super.deleteFile(storePath.getGroup(), storePath.getPath());
    }

	/**
	 * 上传文件
	 * @param multipartFile
	 * @return 返回Null则为失败
	 */
    @Override
	public String uploadFile(byte[] bytes, String fileExt) {
		return uploadFileAndMateData(trackerClient.getStoreStorage(), new ByteArrayInputStream(bytes), bytes.length, fileExt, null).getFullPath();
	}

	/**
	 * 上传文件File
	 * @param multipartFile
	 * @return 返回Null则为失败
	 * @throws FileNotFoundException 
	 */
    @Override
	public String uploadFile(File file) throws FileNotFoundException {
		return uploadFileAndMateData(trackerClient.getStoreStorage(), new FileInputStream(file), file.length(), getFileExt(file.getName()), null).getFullPath();
	}

	/**
	 * MethodName: uploadFile
	 * description: 上传文件“InputStream”
	 * Date: 2016年8月24日 下午5:41:14
	 * @author yangyonghao
	 * @param fis
	 * @param fileExt
	 * @return
	 * @throws IOException 
	 */
    @Override
	public String uploadFile(InputStream fis, String fileExt) throws IOException {
		return uploadFileAndMateData(trackerClient.getStoreStorage(), fis, fis.available(), fileExt, null).getFullPath();
	}

	/**
	 * 获取文件后缀名（不带点）.
	 * @return 如："jpg" or "".
	 */
    @Override
	public String getFileExt(String fileName) {
		if (StringUtils.isEmpty(fileName) || !fileName.contains(".")) {
			return "";
		} else {
			return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase(); // 不带最后的点
		}
	}

	/**
	 * 下载并上传图片
	 * @param imgUrl
	 * @return FastDFS 的fileId
	 * @throws IOException 
	 * @throws Exception
	 */
    @Override
	public String downloadAndUploadImg(String imgUrl) throws IOException {
		// 构造URL
		URL url = new URL(imgUrl);
		// 打开连接
		URLConnection con = url.openConnection();
		// 设置请求超时时间
		con.setConnectTimeout(10 * 1000);
		// 输入流
		InputStream is = con.getInputStream();
		// 1K的数据缓冲
		byte[] bs = new byte[1024];
		// 读取到的数据长度
		int len;
		// 开始读取
		ByteArrayOutputStream os = new ByteArrayOutputStream();  
		while ((len = is.read(bs)) != -1) {
			os.write(bs, 0, len);
		}
		byte[] btImg = os.toByteArray();
		String fileid = uploadFileAndMateData(trackerClient.getStoreStorage(), new ByteArrayInputStream(btImg), btImg.length, "png", null).getFullPath();
		// 完毕，关闭所有链接
		is.close();
		return fileid;
	}
}
