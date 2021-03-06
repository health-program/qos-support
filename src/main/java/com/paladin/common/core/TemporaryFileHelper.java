package com.paladin.common.core;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.paladin.framework.core.configuration.web.WebProperties;
import com.paladin.framework.utils.uuid.UUIDUtil;

@Component
public class TemporaryFileHelper {

	private static Logger logger = LoggerFactory.getLogger(TemporaryFileHelper.class);
	
	@Autowired
	private WebProperties webProperties;

	private String tempPath;
	
	private static TemporaryFileHelper helper;

	@PostConstruct
	protected void initialize() {
		tempPath = webProperties.getFilePath();
		if (tempPath.startsWith("file:")) {
			tempPath = tempPath.substring(5);
		}

		tempPath = tempPath.replaceAll("\\\\", "/");

		if (!tempPath.endsWith("/")) {
			tempPath += "/";
		}

		tempPath += "temp/";

		// 创建目录
		Path root = Paths.get(tempPath);
		try {
			Files.createDirectories(root);
		} catch (IOException e) {
			throw new RuntimeException("创建临时文件存放目录异常", e);
		}
		
		logger.info("临时文件目录：" + tempPath);
		
		helper = this;
	}

	/**
	 * 获取一个临时文件输出流
	 * @param name
	 * @param suffix
	 * @return
	 */
	public TemporaryFileOutputStream createFileOutputStream(String name, String suffix) {

		String id = UUIDUtil.createUUID();

		if (name == null) {
			name = "";
		}

		if (suffix == null) {
			suffix = "";
		}

		String fileName = name + id + "." + suffix;

		try {
			return new TemporaryFileOutputStream(new FileOutputStream(tempPath + fileName), "/temp/" + fileName);
		} catch (Exception e) {
			return null;
		}

	}

	/**
	 * 获取一个临时文件输出流
	 * @param name
	 * @param suffix
	 * @return
	 */
	public static TemporaryFileOutputStream getFileOutputStream(String name, String suffix) {
		return helper.createFileOutputStream(name, suffix);
	}
	
	/**
	 * 清楚临时文件
	 * @param minutes
	 */
	public void clearTemporaryFile(int minutes) {
		Path root = Paths.get(tempPath);
		long time = System.currentTimeMillis() - minutes * 60 * 1000;

		try {
			Files.list(root).forEach(path -> {
				if (path.toFile().lastModified() < time) {
					try {
						Files.delete(path);
					} catch (IOException e) {
						logger.error("删除临时文件[" + path + "]失败");
					}
				}
			});
		} catch (IOException e) {
			logger.error("遍历删除临时文件失败");
		}
	}

	// 0 0 2 * * ? 每天凌晨2点执行
	@Scheduled(cron = "0 0 2 * * ?")
	public void scheduledClean() {
		clearTemporaryFile(30);
	}
	
	public static class TemporaryFileOutputStream extends OutputStream {

		private OutputStream output;
		private String fileRelativeUrl;

		private TemporaryFileOutputStream(OutputStream output, String fileRelativeUrl) {
			this.output = output;
			this.fileRelativeUrl = fileRelativeUrl;
		}

		@Override
		public void write(int b) throws IOException {
			output.write(b);
		}

		@Override
		public void write(byte[] b) throws IOException {
			output.write(b);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			output.write(b, off, len);
		}

		@Override
		public void flush() throws IOException {
			output.flush();
		}

		@Override
		public void close() throws IOException {
			output.close();
		}

		public String getFileRelativeUrl() {
			return fileRelativeUrl;
		}

	}

}
