package org.plavelo.markdown

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

File file = new File( "/Users/plavelo/Desktop/markdown.txt" );
BufferedReader br = null;
try {
	br = new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"));
	// �ǂݍ��񂾕������ێ�����X�g�����O�o�b�t�@��p�ӂ��܂��B
	StringBuffer sb = new StringBuffer();
	// �t�@�C������ǂݍ��񂾈ꕶ����ۑ�����ϐ��ł��B
	int c;
	// �t�@�C������P�������ǂݍ��݁A�o�b�t�@�֒ǉ����܂��B
	while ((c = br.read()) != -1) {
	  sb.append((char) c);
	}
	// �o�b�t�@�̓��e�𕶎��񉻂��ĕԂ��܂��B
	//String source = "*italic* **bold**\n_italic_ __bold__";
	String source = sb.toString();
	println new Markdown().markdown(source)
} catch (FileNotFoundException e) {
	e.printStackTrace();
} catch (IOException e) {
	e.printStackTrace();
}

