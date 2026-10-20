package org.javaup.exception;

import lombok.Data;

/**
 * @program: movie-ticket
 * @description: 参数错误
 * @author: xiong
 **/
@Data
public class ArgumentError {
	
	private String argumentName;
	
	private String message;
}
