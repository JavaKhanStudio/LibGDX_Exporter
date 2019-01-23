package jks.export.tmx;

import java.io.BufferedWriter;
// Un grand merci a Robin Stumm
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Iterator;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import com.badlogic.gdx.maps.Map;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapLayers;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.objects.CircleMapObject;
import com.badlogic.gdx.maps.objects.EllipseMapObject;
import com.badlogic.gdx.maps.objects.PolygonMapObject;
import com.badlogic.gdx.maps.objects.PolylineMapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell;
import com.badlogic.gdx.maps.tiled.TiledMapTileSet;
import com.badlogic.gdx.maps.tiled.TiledMapTileSets;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Ellipse;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Polyline;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Base64Coder;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.Pools;
import com.badlogic.gdx.utils.StringBuilder;
import com.badlogic.gdx.utils.XmlWriter;

import jks.export.enums.Enum_FileFormat;
import jks.export.enums.Enum_MapProperty;

import static com.badlogic.gdx.math.MathUtils.round;
import static jks.export.enums.Enum_FileFormat.*; 

public class Utils_MapSavingTMX extends XmlWriter 
{


	/** The height of a layer <strong>IN PIXELS</strong>, to invert the y-axis. {@link #setLayerHeight(int) Set} this explicitly if you want to write something that does not know the layer size, like a {@link #saveLayer(MapLayer) single} or {@link #saveLayers(MapLayers, Format) multiple} layers or {@link #tmx(MapObject) object}{@link #tmx(MapObjects) s}. */
	private int layerHeight;

	/** creates a new {@link Utils_MapSavingTMX} using the given {@link Writer} */
	public Utils_MapSavingTMX(Writer writer) 
	{super(writer);}

	/** @param map the {@link Map} to write in TMX format
	 *  @param format the {@link Format} to use
	 *  @return this {@link Utils_MapSavingTMX} */
	public Utils_MapSavingTMX save(Map map, Enum_FileFormat format) throws IOException 
	{
		append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		append("<!DOCTYPE map SYSTEM \"http://mapeditor.org/dtd/1.0/map.dtd\">\n");
	
		MapProperties props = map.getProperties();
		int width = getProperty(props, Enum_MapProperty.width.name(), 0) ; 
		int height = getProperty(props, Enum_MapProperty.height.name(), 0);
		int tileHeight = getProperty(props, Enum_MapProperty.tileheight.name(), 0);
		int oldLayerHeight = layerHeight;
		layerHeight = height * tileHeight;

		element("map");
		attribute("version", "1.0");
		attribute("orientation", getProperty(props, "orientation", "orthogonal"));
		attribute("width", width);
		attribute("height", height);
		attribute("tilewidth", getProperty(props, "tilewidth", 0));
		attribute("tileheight", tileHeight);
		@SuppressWarnings("unchecked")
		Array<String> excludedKeys = Pools.obtain(Array.class);
		excludedKeys.clear();
		excludedKeys.add("version");
		excludedKeys.add("orientation");
		excludedKeys.add("width");
		excludedKeys.add("height");
		excludedKeys.add("tilewidth");
		excludedKeys.add("tileheight");
		saveProperties(props, excludedKeys);
		excludedKeys.clear();
		Pools.free(excludedKeys);

		if(map instanceof TiledMap)
			saveTileSets(((TiledMap) map).getTileSets());

		saveLayers(map.getLayers(), format);

		pop();

		layerHeight = oldLayerHeight;
		return this;
	}

	/** @param properties the {@link MapProperties} to write in TMX format
	 *  @return this {@link Utils_MapSavingTMX} */
	public Utils_MapSavingTMX saveProperties(MapProperties properties) throws IOException 
	{return saveProperties(properties, null);}

	/** writes nothing if the given {@link MapProperties} are empty or every key is excluded
	 *  @param properties the {@link MapProperties} to write in TMX format
	 *  @param exclude the keys not to write
	 *  @return this {@link Utils_MapSavingTMX} */
	public Utils_MapSavingTMX saveProperties(MapProperties properties, Array<String> exclude) throws IOException 
	{
		Iterator<String> keys = properties.getKeys();
		if(!keys.hasNext())
			return this;

		boolean elementEmitted = false;
		while(keys.hasNext()) 
		{
			String key = keys.next();
			if(exclude != null && exclude.contains(key, false))
				continue;
			
			if(!elementEmitted) 
			{
				element("properties");
				elementEmitted = true;
			}
			element("property").attribute("name", key).attribute("value", properties.get(key)).pop();
		}

		if(elementEmitted)
			pop();
		
		return this;
	}

	/** @param sets the {@link TiledMapTileSets} to write in TMX format
	 *  @return this {@link Utils_MapSavingTMX} */
	public Utils_MapSavingTMX saveTileSets(TiledMapTileSets sets) throws IOException 
	{
		for(TiledMapTileSet set : sets)
		{saveTileSet(set);}
			
		return this;
	}

	/** @param set the {@link TiledMapTileSet} to write in TMX format
	 *  @return this {@link Utils_MapSavingTMX} */
	public Utils_MapSavingTMX saveTileSet(TiledMapTileSet set) throws IOException 
	{
		MapProperties props = set.getProperties();
		element("tileset");
		attribute("firstgid", getProperty(props, "firstgid", 1));
		attribute("name", set.getName());
		attribute("tilewidth", getProperty(props, "tilewidth", 0));
		attribute("tileheight", getProperty(props, "tileheight", 0));
		float spacing = getProperty(props, "spacing", Float.NaN), margin = getProperty(props, "margin", Float.NaN);
		
		if(!Float.isNaN(spacing))
			attribute("spacing", round(spacing));
		
		if(!Float.isNaN(margin))
			attribute("margin", round(margin));

		Iterator<TiledMapTile> iter = set.iterator();
		if(iter.hasNext()) 
		{
			TiledMapTile tile = iter.next();
			element("tileoffset");
			attribute("x", round(tile.getOffsetX()));
			attribute("y", round(-tile.getOffsetY()));
			pop();
		}

		element("image");
		attribute("source", getProperty(props, "imagesource", ""));
		attribute("imagewidth", getProperty(props, "imagewidth", 0));
		attribute("imageheight", getProperty(props, "imageheight", 0));
		pop();

		iter = set.iterator();
		if(iter.hasNext()) 
		{
			@SuppressWarnings("unchecked")
			Array<String> asAttributes = Pools.obtain(Array.class);
			asAttributes.clear();
			boolean elementEmitted = false;
			for(TiledMapTile tile = iter.next(); iter.hasNext(); tile = iter.next()) 
			{
				MapProperties tileProps = tile.getProperties();
				for(String attribute : asAttributes)
				{
					if(tileProps.containsKey(attribute)) 
					{
						if(!elementEmitted) {
							element("tile");
							elementEmitted = true;
						}
						attribute(attribute, tileProps.get(attribute));
					}
				}
				saveProperties(tileProps, asAttributes);
			}
			
			asAttributes.clear();
			Pools.free(asAttributes);
			if(elementEmitted)
				pop();
		}

		pop();
		return this;
	}

	/** @param layers the {@link MapLayers}
	 *  @param format the {@link Format} to use
	 *  @return this {@link Utils_MapSavingTMX} */
	public Utils_MapSavingTMX saveLayers(MapLayers layers, Enum_FileFormat format) throws IOException 
	{
		for(MapLayer layer : layers)
		{
			if(layer instanceof TiledMapTileLayer)
				saveTileLayer((TiledMapTileLayer) layer, format);
			else
				saveLayer(layer);
		}
			
		return this;
	}

	/** @param layer the {@link MapLayer} to write in TMX format
	 *  @return this {@link Utils_MapSavingTMX} */
	public Utils_MapSavingTMX saveLayer(MapLayer layer) throws IOException 
	{
		element("objectgroup");
		attribute("name", layer.getName());
		saveProperties(layer.getProperties());
		tmx(layer.getObjects());
		pop();
		return this;
	}

	/** @param layer the {@link TiledMapTileLayer} to write in TMX format
	 *  @param format the {@link Format} to use
	 *  @return this {@link Utils_MapSavingTMX} */
	public Utils_MapSavingTMX saveTileLayer(TiledMapTileLayer layer, Enum_FileFormat format) throws IOException 
	{
		element("layer");
		attribute("name", layer.getName());
		attribute("width", layer.getWidth());
		attribute("height", layer.getHeight());
		attribute("visible", layer.isVisible() ? 1 : 0);
		attribute("opacity", layer.getOpacity());

		saveProperties(layer.getProperties());

		element("data");
		switch(format)
		{
			case XML :
			{
				attribute("encoding", "xml");
				for(int y = layer.getHeight() - 1; y > -1; y--)
				{
					for(int x = 0; x < layer.getWidth(); x++) 
					{
						Cell cell = layer.getCell(x, y);
						if(cell != null) 
						{
							TiledMapTile tile = cell.getTile();
							
							if(tile == null)
								continue;
							
							element("tile");
							attribute("gid", tile.getId());
							pop();
						}
					}
				}
				break ;
			}
			case CSV :
			{
				attribute("encoding", "csv");
				StringBuilder csv = new StringBuilder();
				for(int y = layer.getHeight() - 1; y > -1; y--) 
				{
					for(int x = 0; x < layer.getWidth(); x++) 
					{
						Cell cell = layer.getCell(x, y);
						TiledMapTile tile = cell != null ? cell.getTile() : null;
						csv.append(tile != null ? tile.getId() : 0);
						
						if(x + 1 < layer.getWidth() || y - 1 > -1)
							csv.append(',');
					}
					csv.append('\n');
				}
				append('\n').append(csv);
				break ; 
			}
			case Base64 :
			case Base64Gzip :
			case Base64Zlib :
			{
				attribute("encoding", "base64");
				
				if(format == Base64Zlib)
					attribute("compression", "zlib");
				else if(format == Base64Gzip)
					attribute("compression", "gzip");
				
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				OutputStream out = format == Base64Zlib ? new DeflaterOutputStream(baos) : format == Base64Gzip ? new GZIPOutputStream(baos) : baos;
				final short LAST_BYTE = 0xFF;
				
				for(int y = layer.getHeight() - 1; y > -1; y--)
				{
					for(int x = 0; x < layer.getWidth(); x++) 
					{
						Cell cell = layer.getCell(x, y);
						TiledMapTile tile = cell != null ? cell.getTile() : null;
						int gid = tile != null ? tile.getId() : 0;
						out.write(gid & LAST_BYTE);
						out.write(gid >> 8 & LAST_BYTE);
						out.write(gid >> 16 & LAST_BYTE);
						out.write(gid >> 24 & LAST_BYTE);
					}
				}
				
				if(out instanceof DeflaterOutputStream)
					((DeflaterOutputStream) out).finish();
				
				out.close();
				baos.close();
				append('\n').append(String.valueOf(Base64Coder.encode(baos.toByteArray()))).append('\n');
				pop();
				break ;
			}
		}
		
		pop();
		return this;
	}
	
	public void saveMap(TiledMap map)
	{
		Writer write;
		try 
		{
			write = new FileWriter("C:\\Users\\Simon\\Documents\\TestGDX\\Test.txt");
			BufferedWriter bw = new BufferedWriter(write);
			Utils_MapSavingTMX mapWriter = new Utils_MapSavingTMX(bw) ; 
			mapWriter.save(map, Base64) ;
			bw.flush();
			bw.close();
		} 
		catch (IOException e) 
		{e.printStackTrace();} 
	}

	/** @param objects the {@link MapObject} to write in TMX format
	 *  @return this {@link Utils_MapSavingTMX} */
	public Utils_MapSavingTMX tmx(MapObjects objects) throws IOException 
	{
		for(MapObject object : objects)
			tmx(object);
		
		return this;
	}

	/** @param object the {@link MapObject} to write in TMX format
	 *  @return this {@link Utils_MapSavingTMX} */
	public Utils_MapSavingTMX tmx(MapObject object) throws IOException 
	{
		MapProperties props = object.getProperties();
		element("object");
		attribute("name", object.getName());
		
		if(props.containsKey("type"))
			attribute("type", getProperty(props, "type", ""));
		if(props.containsKey("gid"))
			attribute("gid", getProperty(props, "gid", 0));

		int objectX = getProperty(props, "x", 0);
		int objectY = getProperty(props, "y", 0);

		if(object instanceof RectangleMapObject)
		{
			Rectangle rect = ((RectangleMapObject) object).getRectangle();
			int height = round(rect.height);
			attribute("x", objectX).attribute("y", roundedToYDown(objectY + height));
			attribute("width", round(rect.width)).attribute("height", height);
		}
		else if(object instanceof EllipseMapObject) 
		{
			Ellipse ellipse = ((EllipseMapObject) object).getEllipse();
			int height = round(ellipse.height);
			attribute("x", objectX).attribute("y", roundedToYDown(objectY + height));
			attribute("width", round(ellipse.width)).attribute("height", height);
			element("ellipse").pop();
		}
		else if(object instanceof CircleMapObject) 
		{
			Circle circle = ((CircleMapObject) object).getCircle();
			attribute("x", objectX).attribute("y", objectY);
			attribute("width", round(circle.radius * 2)).attribute("height", round(circle.radius * 2));
			element("ellipse").pop();
		} 
		else if(object instanceof PolygonMapObject) 
		{
			attribute("x", objectX).attribute("y", roundedToYDown(objectY));
			Polygon polygon = ((PolygonMapObject) object).getPolygon();
			element("polygon");
			FloatArray tmp = Pools.obtain(FloatArray.class);
			tmp.clear();
			tmp.addAll(polygon.getVertices());
			attribute("points", points(toYDownEX(tmp)));
			tmp.clear();
			Pools.free(tmp);
			pop();
		} 
		else if(object instanceof PolylineMapObject) 
		{
			attribute("x", objectX).attribute("y", roundedToYDown(objectY));
			Polyline polyline = ((PolylineMapObject) object).getPolyline();
			element("polyline");
			FloatArray tmp = Pools.obtain(FloatArray.class);
			tmp.clear();
			tmp.addAll(polyline.getVertices());
			attribute("points", points(toYDownEX(tmp)));
			tmp.clear();
			Pools.free(tmp);
			pop();
		}

		if(props.containsKey("rotation"))
			attribute("rotation", getProperty(props, "rotation", 0f));
		if(props.containsKey("visible"))
			attribute("visible", object.isVisible() ? 1 : 0);
		if(object.getOpacity() != 1)
			attribute("opacity", object.getOpacity());

		@SuppressWarnings("unchecked")
		Array<String> excludedKeys = Pools.obtain(Array.class);
		excludedKeys.clear();
		excludedKeys.add("type");
		excludedKeys.add("gid");
		excludedKeys.add("x");
		excludedKeys.add("y");
		excludedKeys.add("width");
		excludedKeys.add("height");
		excludedKeys.add("rotation");
		excludedKeys.add("visible");
		excludedKeys.add("opacity");
		saveProperties(props, excludedKeys);
		excludedKeys.clear();
		Pools.free(excludedKeys);

		pop();
		return this;
	}

	/** @param vertices the vertices to arrange in TMX format
	 *  @return a String of the given vertices ready for use in TMX maps */
	private String points(FloatArray vertices) 
	{
		StringBuilder points = new StringBuilder();
		
		for(int i = 0; i < vertices.size; i++)
			points.append(round(vertices.get(i))).append(i % 2 != 0 ? i + 1 < vertices.size ? " " : "" : ",");
		
		return points.toString();
	}

	private static float[] floats = new float[Byte.MAX_VALUE];

	public static FloatArray toYDownEX(FloatArray vertices) {
		toYDownEX(vertices.items, 0, vertices.size);
		return vertices;
	}
	
	public static float[] toYDownEX(float[] vertices, int offset, int length) {
		checkRegion(vertices, offset, length);
		invertAxes(vertices, offset, length, false, true);
		return subY(vertices, offset, length, height(vertices, offset, length));
	}
	
	public static float[] subY(float[] items, int offset, int length, float value) {
		return sub(items, offset, length, 0, value);
	}
	
	public static float[] sub(float[] items, int offset, int length, float x, float y) {
		return add(items, offset, length, -x, -y);
	}
	
	public static float[] add(float[] items, int offset, int length, float x, float y) {
		for(int i = offset + 1; i < offset + length; i += 2) {
			items[i - 1] += x;
			items[i] += y;
		}
		return items;
	}
	
	public static float[] invertAxes(float[] vertices, int offset, int length, boolean x, boolean y) {
		if(!x && !y)
			return vertices;
		float height = height(vertices, offset, length), width = width(vertices, offset, length);
		for(int i = (x ? 0 : 1) + offset; i < offset + length; i += x ^ y ? 2 : 1)
			vertices[i] = i % 2 == 0 ? invertAxis(vertices[i], width) : invertAxis(vertices[i], height);
		return vertices;
	}
	
	public static void checkRegion(float[] array, int offset, int length) {
		if(array == null)
			throw new NullPointerException("array is null");
		if(offset < 0)
			throw new ArrayIndexOutOfBoundsException("negative offset: " + offset);
		if(length < 0)
			throw new ArrayIndexOutOfBoundsException("negative length: " + length);
		if(offset + length > array.length)
			throw new ArrayIndexOutOfBoundsException(offset + length);
	}
	
	
	
	public static float height(float[] vertices, int offset, int length) {
		return amplitude2(filterY(vertices, offset, length, floats), 0, length / 2);
	}
	
	public static float[] filterY(float[] vertices, int offset, int length, float[] dest, int destOffset) {
		checkRegion(vertices, offset, length);
		return select(vertices, offset, length, 0, 2, dest, destOffset);
	}
	
	public static float[] select(float[] items, int offset, int length, int start, int everyXth, float[] dest, int destOffset) {
		int outputLength = selectCount(offset, length, start, everyXth);
		checkRegion(dest, destOffset, outputLength);
		for(int di = destOffset, i = start - 1; di < outputLength; i += everyXth)
			if(i >= offset) {
				dest[di] = items[i];
				di++;
			}
		return dest;
	}
	
	public static int selectCount(int offset, int length, int start, int everyXth) {
		int count = 0;
		for(int i = start - 1; i < offset + length; i += everyXth)
			if(i >= offset)
				count++;
		return count;
	}
	
	public static float[] filterY(float[] vertices, int offset, int length, float[] dest) {
		return filterY(vertices, offset, length, dest, 0);
	}
	
	public static float width(float[] vertices, int offset, int length) {
		return amplitude2(filterX(vertices, offset, length, floats), 0, length / 2);
	}
	
	public static float[] filterX(float[] vertices, int offset, int length, float[] dest) {
		return filterX(vertices, offset, length, dest, 0);
	}
	
	public static float[] filterX(float[] vertices, int offset, int length, float[] dest, int destOffset) {
		checkRegion(vertices, offset, length);
		return select(vertices, offset, length, -1, 2, dest, destOffset);
	}

	public int roundedToYDown(int y) 
	{return round(toYDown((float) y));}

	/** @param y the y coordinate
	 *  @return the y coordinate converted from a y-up to a y-down coordinate system */
	public float toYDown(float y) 
	{return invertAxis(y, layerHeight);}
	
	public static float invertAxis(float coord, float axisSize) 
	{return mirror(coord, axisSize / 2);}
	
	public static float mirror(float value, float baseline) 
	{return baseline * 2 - value;}
	
	/** Makes sure the return value is of the desired type (null-safe). If the value of the property is not of the desired type, it will be parsed.
	 *  @param properties the {@link MapProperties} to get the value from
	 *  @param key the key of the property
	 *  @param defaultValue the value to return in case the value was null or an empty String or couldn't be returned
	 *  @return the key's value as the type of defaultValue */
	@SuppressWarnings("unchecked")
	public static <T> T getProperty(MapProperties properties, String key, T defaultValue) {
		if(properties == null || key == null)
			return defaultValue;

		Object value = properties.get(key);

		if(value == null || value instanceof String && ((String) value).length() == 0)
			return defaultValue;

		if(defaultValue != null) {
			if(defaultValue.getClass() == Boolean.class && !(value instanceof Boolean))
				return (T) Boolean.valueOf(value.toString());

			if(defaultValue.getClass() == Integer.class && !(value instanceof Integer))
				return (T) Integer.valueOf(Float.valueOf(value.toString()).intValue());

			if(defaultValue.getClass() == Float.class && !(value instanceof Float))
				return (T) Float.valueOf(value.toString());

			if(defaultValue.getClass() == Double.class && !(value instanceof Double))
				return (T) Double.valueOf(value.toString());

			if(defaultValue.getClass() == Long.class && !(value instanceof Long))
				return (T) Long.valueOf(value.toString());

			if(defaultValue.getClass() == Short.class && !(value instanceof Short))
				return (T) Short.valueOf(value.toString());

			if(defaultValue.getClass() == Byte.class && !(value instanceof Byte))
				return (T) Byte.valueOf(value.toString());
		}

		return (T) value;
	}
	
	/** @see #amplitude2(float[], int, int) */
	public static float amplitude2(float[] items) {
		return amplitude2(items, 0, items.length);
	}

	public static float amplitude2(float[] items, int offset, int length) {
		return max(items, offset, length) - min(items, offset, length);
	}
	
	public static float max(float[] items, int offset, int length) {
		checkRegion(items, offset, length);
		if(length == 0)
			return Float.NaN;
		float max = Float.NEGATIVE_INFINITY;
		for(int i = offset; i < offset + length; i++) {
			float f = items[i];
			if(f > max)
				max = f;
		}
		return max;
	}
	
	public static float min(float[] items, int offset, int length) {
		checkRegion(items, offset, length);
		if(length == 0)
			return Float.NaN;
		float min = Float.POSITIVE_INFINITY;
		for(int i = offset; i < offset + length; i++) {
			float f = items[i];
			if(f < min)
				min = f;
		}
		return min;
	}
	
	
	// getters and setters

	/** @return the {@link #layerHeight} */
	public int getLayerHeight() 
	{return layerHeight;}

	/** @param layerHeight the {@link #layerHeight} to set */
	public void setLayerHeight(int layerHeight) 
	{this.layerHeight = layerHeight;}

}