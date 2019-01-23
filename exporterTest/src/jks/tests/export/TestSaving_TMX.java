package jks.tests.export;


import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;

import jks.export.enums.Enum_FileFormat;
import jks.export.tmx.Utils_MapSavingTMX;

public class TestSaving_TMX extends GdxTest 
{

	public static void main (String[] arg) 
	{
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
//		Utils_Launcher.basicConfig(config) ; 
		new LwjglApplication(new TestSaving_TMX(), config);
		
	}
	
	@Override
	public void create () 
	{
		
		TiledMap map = new TmxMapLoader().load("map/test_short.tmx");
		
		Writer write;
		try 
		{
			write = new FileWriter("C:\\Users\\Simon\\Documents\\TestGDX\\Test" + ".tmx");
			BufferedWriter bw = new BufferedWriter(write);
			Utils_MapSavingTMX mapWriter = new Utils_MapSavingTMX(bw) ; 
			mapWriter.save(map, Enum_FileFormat.Base64) ;
			bw.flush();
			bw.close();
		} 
		catch (IOException e) 
		{e.printStackTrace();} 
	
		System.out.println();
		System.exit(1);
	}

	@Override
	public void render () 
	{
		
	}
}