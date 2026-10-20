package org.javaup.exception;


import lombok.Data;
import lombok.EqualsAndHashCode;
import org.javaup.enums.BaseCode;

/**
 * @program: movie-ticket
 * @description: 业务异常
 * @author: xiong
 **/
@EqualsAndHashCode(callSuper = true)
@Data
public class MovieFrameException extends BaseException {
	
	private Integer code;
	
	private String message;

	public MovieFrameException() {
		super();
	}

	public MovieFrameException(String message) {
		super(message);
	}
	
	public MovieFrameException(Integer code, String message) {
		super(message);
		this.code = code;
		this.message = message;
	}
	
	public MovieFrameException(BaseCode baseCode) {
		super(baseCode.getMsg());
		this.code = baseCode.getCode();
		this.message = baseCode.getMsg();
	}

	public MovieFrameException(Throwable cause) {
		super(cause);
	}

	public MovieFrameException(String message, Throwable cause) {
		super(message, cause);
		this.message = message;
	}
}
