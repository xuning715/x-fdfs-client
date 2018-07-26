package com.github.tobato.fastdfs.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import com.github.tobato.fastdfs.domain.MateData;
import com.github.tobato.fastdfs.domain.StorePath;

/**
 * 面向普通应用的文件操作接口封装
 * 
 * @author tobato
 *
 */
public interface FastFileStorageClient extends GenerateStorageClient {

	/**
	 * 上传一般文件
	 * @param inputStream
	 * @param fileSize
	 * @param fileExtName
	 * @param metaDataSet
	 * @return
	 */
	StorePath uploadFile(InputStream inputStream, long fileSize, String fileExtName, Set<MateData> metaDataSet);

	/**
	 * 上传图片并且生成缩略图
	 * <pre>
	 * 支持的图片格式包括"JPG", "JPEG", "PNG", "GIF", "BMP", "WBMP"
	 * </pre>
	 * @param inputStream
	 * @param fileSize
	 * @param fileExtName
	 * @param metaDataSet
	 * @return
	 */
	StorePath uploadImageAndCrtThumbImage(InputStream inputStream, long fileSize, String fileExtName,
			Set<MateData> metaDataSet);

	/**
	 * 删除文件
	 * @param filePath 文件路径(groupName/path)
	 */
	void deleteFile(String filePath);

	/**
	 * 上传文件
	 * @param bytes
	 * @param fileExt
	 * @return 返回Null则为失败
	 */
	String uploadFile(byte[] bytes, String fileExt);

	/**
	 * 上传文件File
	 * @param file
	 * @return 返回Null则为失败
	 * @throws FileNotFoundException 
	 */
	public String uploadFile(File file) throws FileNotFoundException;

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
	public String uploadFile(InputStream fis, String fileExt) throws IOException;

	/**
	 * 下载并上传图片
	 * @param imgUrl
	 * @return FastDFS 的fileId
	 * @throws IOException 
	 * @throws Exception
	 */
	public String downloadAndUploadImg(String imgUrl) throws IOException;

	/**
	 * 获取文件后缀名（不带点）.
	 * @return 如："jpg" or "".
	 */
	public String getFileExt(String fileName);
}
