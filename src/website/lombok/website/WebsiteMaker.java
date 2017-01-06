package lombok.website;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.java2html.Java2Html;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.core.HTMLOutputFormat;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

public class WebsiteMaker {
	private final File baseDir, outputDir;
	
	public WebsiteMaker(File baseDir, File outputDir) {
		this.baseDir = baseDir;
		this.outputDir = outputDir;
	}
	
	public static void main(String[] args) throws Exception {
		File in, out;
		if (args.length == 0) {
			in = new File(".");
			if (new File(in, "build.xml").isFile() && new File(in, "website2").isDirectory()) in = new File(in, "website2");
		} else {
			in = new File(args[0]);
		}
		
		if (args.length < 2) {
			if (new File("./build.xml").isFile() && new File("./website2").isDirectory() && new File("./build").isDirectory()) {
				out = new File("./build/website2");
			} else {
				out = new File(in, "output");
			}
		} else {
			out = new File(args[1]);
		}
		WebsiteMaker maker = new WebsiteMaker(in, out);
		
		maker.buildWebsite();
	}
	
	public void buildWebsite() throws Exception {
		Configuration freemarkerConfig = new Configuration(Configuration.VERSION_2_3_25);
		freemarkerConfig.setEncoding(Locale.ENGLISH, "UTF-8");
		freemarkerConfig.setOutputEncoding("UTF-8");
		freemarkerConfig.setOutputFormat(HTMLOutputFormat.INSTANCE);
		freemarkerConfig.setTemplateLoader(createLoader());
		freemarkerConfig.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
		
		deleteAll(outputDir, 0);
		outputDir.mkdirs();
		copyResources();
		convertTemplates(freemarkerConfig);
	}
	
	private static void deleteAll(File outputDir, int depth) {
		if (!outputDir.isDirectory()) return;
		if (depth > 50) throw new IllegalArgumentException("50 levels is too deep: " + outputDir);
		
		for (File f : outputDir.listFiles()) {
			String n = f.getName();
			if (n.equals(".") || n.equals("..")) continue;
			if (f.isDirectory()) deleteAll(f, depth + 1);
			f.delete();
		}
	}
	
	private void copyResources() throws Exception {
		File resourcesLoc = new File(baseDir, "resources");
		byte[] b = new byte[65536];
		copyResources_(resourcesLoc, outputDir, b, 0);
	}
	
	private void copyResources_(File from, File to, byte[] b, int depth) throws IOException {
		if (depth > 50) throw new IllegalArgumentException("50 levels is too deep: " + from);
		if (!to.exists()) to.mkdirs();
		for (File f : from.listFiles()) {
			if (f.isDirectory()) copyResources_(f, new File(to, f.getName()), b, depth + 1);
			if (!f.isFile()) continue;
			
			FileInputStream fIn = new FileInputStream(f);
			try {
				FileOutputStream fOut = new FileOutputStream(new File(to, f.getName()));
				try {
					while (true) {
						int r = fIn.read(b);
						if (r == -1) break;
						fOut.write(b, 0, r);
					}
				} finally {
					fOut.close();
				}
			} finally {
				fIn.close();
			}
		}
	}
	
	private TemplateLoader createLoader() throws IOException {
		return new FileTemplateLoader(new File(baseDir, "templates"));
	}
	
	private void convertTemplates(Configuration freemarker) throws Exception {
		File basePagesLoc = new File(baseDir, "templates");
		convertTemplates_(freemarker, "", basePagesLoc, outputDir, 0);
	}
	
	private void convertTemplates_(Configuration freemarker, String prefix, File from, File to, int depth) throws Exception {
		if (depth > 50) throw new IllegalArgumentException("50 levels is too deep: " + from);
		
		Map<String, Object> dataModel = createDataModel();
		
		for (File f : from.listFiles()) {
			if (f.isDirectory()) convertTemplates_(freemarker, prefix + f.getName() + "/", f, new File(to, f.getName()), depth + 1);
			if (!f.isFile() || !f.getName().endsWith(".html") || f.getName().startsWith("_")) continue;
			to.mkdirs();
			Template template = freemarker.getTemplate(prefix + f.getName());
			FileOutputStream fileOut = new FileOutputStream(new File(to, f.getName()));
			try {
				Writer wr = new BufferedWriter(new OutputStreamWriter(fileOut, "UTF-8"));
				template.process(dataModel, wr);
				wr.close();
			} finally {
				fileOut.close();
			}
		}
	}
	
	private static final Pattern LOMBOK_LINK = Pattern.compile("^.*<a(?: (?:id|class|rel|rev|download|target|type)(?:=\"[^\"]*\")?)* href=\"([^\"]+)\"(?: (?:id|class|rel|rev|download|target|type)(?:=\"[^\"]*\")?)*>([^<]+)</a>.*$");
	private Map<String, Object> createDataModel() throws IOException {
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("version", lombok.core.Version.getVersion());
		data.put("fullVersion", lombok.core.Version.getFullVersion());
		data.put("year", "" + new GregorianCalendar().get(Calendar.YEAR));
		data.put("usages", new HtmlMaker(new File(baseDir, "usageExamples")));
		InputStream in = new URL("https://projectlombok.org/all-versions.html").openStream();
		ArrayList<List<String>> links = new ArrayList<List<String>>();
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
			for (String line = br.readLine(); line != null; line = br.readLine()) {
				Matcher m = LOMBOK_LINK.matcher(line);
				if (m.matches()) links.add(Arrays.asList(m.group(1), m.group(2)));
			}
		} finally {
			in.close();
		}
		
		if (links.isEmpty() || !links.get(0).get(0).endsWith("lombok-" + lombok.core.Version.getVersion() + ".jar")) {
			links.add(Arrays.asList("downloads/lombok-" + lombok.core.Version.getVersion() + ".jar", "lombok-" + lombok.core.Version.getVersion() + ".jar"));
		}
		
		data.put("linksToVersions", links);
		return data;
	}
	
	public static class HtmlMaker {
		private final File usagesDir;
		
		HtmlMaker(File usagesDir) {
			this.usagesDir = usagesDir;
		}
		
		public String pre(String name) throws IOException {
			return convert(new File(usagesDir, name + "Example_pre.jpage"));
		}
		
		public String post(String name) throws IOException {
			return convert(new File(usagesDir, name + "Example_post.jpage"));
		}
		
		public String convert(File file) throws IOException {
			String rawJava = readFully(file);
			return Java2Html.convertToHtml(rawJava);
		}
	}
	
	public static String readFully(File file) throws IOException {
		FileInputStream fis = new FileInputStream(file);
		try {
			InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
			StringBuilder out = new StringBuilder();
			char[] b = new char[65536];
			while (true) {
				int r = isr.read(b);
				if (r == -1) break;
				out.append(b, 0, r);
			}
			return out.toString();
		} finally {
			fis.close();
		}
	}
}
