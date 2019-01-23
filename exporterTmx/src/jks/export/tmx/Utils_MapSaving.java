package jks.export.tmx;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import com.badlogic.gdx.maps.tiled.TiledMap;

import jks.export.enums.Enum_FileFormat;

public class Utils_MapSaving 
{

	public static void saveThisMap(TiledMap map,String path,String mapName)
	{
		Writer write;
		try 
		{
			write = new FileWriter(path + "\\" + mapName + ".tmx");
			BufferedWriter bw = new BufferedWriter(write);
			Utils_MapSavingTMX mapWriter = new Utils_MapSavingTMX(bw) ; 
			mapWriter.save(map, Enum_FileFormat.Base64) ;
			bw.flush();
			bw.close();
		} 
		catch (IOException e) 
		{e.printStackTrace();} 
	}
	
	
}
