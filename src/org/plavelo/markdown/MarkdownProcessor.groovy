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
	// 読み込んだ文字列を保持するストリングバッファを用意します。
	StringBuffer sb = new StringBuffer();
	// ファイルから読み込んだ一文字を保存する変数です。
	int c;
	// ファイルから１文字ずつ読み込み、バッファへ追加します。
	while ((c = br.read()) != -1) {
	  sb.append((char) c);
	}
	// バッファの内容を文字列化して返します。
	//String source = "*italic* **bold**\n_italic_ __bold__";
	String source = sb.toString();
	println new Markdown().markdown(source)
} catch (FileNotFoundException e) {
	e.printStackTrace();
} catch (IOException e) {
	e.printStackTrace();
}

