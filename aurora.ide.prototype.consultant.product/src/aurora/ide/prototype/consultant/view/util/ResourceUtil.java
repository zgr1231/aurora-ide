package aurora.ide.prototype.consultant.view.util;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import uncertain.composite.CompositeMap;
import uncertain.composite.XMLOutputter;
import aurora.ide.helpers.CompositeMapUtil;
import aurora.ide.prototype.consultant.product.fsd.wizard.FSDContentControl;
import aurora.ide.prototype.consultant.view.wizard.CreateFunctionWizard;
import aurora.ide.prototype.consultant.view.wizard.CreateModuleWizard;
import aurora.ide.prototype.consultant.view.wizard.CreateProjectWizard;

public class ResourceUtil {

	public static boolean isProject(File pj) {
		File p = new File(pj, CreateProjectWizard.QUICK_UI_PROJECT);
		return p.exists();
	}

	public static boolean isModule(File file) {
		File p = new File(file, CreateModuleWizard.QUICK_UI_MODULE);
		return p.exists();
	}

	public static boolean isFunction(File file) {
		File p = new File(file, CreateFunctionWizard.QUICK_UI_FUNCTION);
		return p.exists();
	}

	public static CompositeMap loadProjectProperties(File file) {
		File p = new File(file, CreateProjectWizard.QUICK_UI_PROJECT);
		if (p.exists() == false)
			return null;
		CompositeMap loadFile = CompositeMapUtil.loadFile(p);
		return loadFile;
	}

	public static CompositeMap loadFunctionProperties(File file) {
		File p = new File(file, CreateFunctionWizard.QUICK_UI_FUNCTION);
		if (p.exists() == false)
			return null;
		CompositeMap loadFile = CompositeMapUtil.loadFile(p);
		return loadFile;
	}
	public static File getProject(File file) {
		if (isProject(file)) {
			return file;
		} else if (file.getParentFile().exists()) {
			return getProject(file.getParentFile());
		} else {
			return file;
		}
	}

	public static IPath getFullProjectRelativePath(File project, File f) {
		IPath pjPath = new Path(project.getPath());
		IPath pPath = new Path(f.getPath());
		IPath r = new Path(project.getName() + File.separator
				+ pPath.makeRelativeTo(pjPath));
		return r;
	}

	public static File createFolder(File f) {
		if (f.mkdir()) {
			return f;
		} else {
			return null;
		}
	}

	public static void createFile(File parent, String name, CompositeMap map)
			throws IOException {
		File p_file = new File(parent, name);
		p_file.createNewFile();
		if (p_file.exists()) {
			if (p_file.canWrite()) {
				XMLOutputter.saveToFile(p_file, map);
			}
		}
	}

	public static boolean isUIP(File file) {
		IPath p = new Path(file.getPath());
		return "uip".equalsIgnoreCase(p.getFileExtension());
	}

	public static CompositeMap loadModuleProperties(File file) {
		File p = new File(file, CreateModuleWizard.QUICK_UI_MODULE);
		if (p.exists() == false)
			return null;
		CompositeMap loadFile = CompositeMapUtil.loadFile(p);
		return loadFile;
	
	}
	
	public static  void copyProjectProperties(File file, CompositeMap pp) {
		File project = ResourceUtil.getProject(file);
		CompositeMap projectProperties = ResourceUtil
				.loadProjectProperties(project);
		List childs = projectProperties.getChilds();
		if (childs == null)
			return;
		for (Object c : childs) {
			if (c instanceof CompositeMap) {
				if (FSDContentControl.FSD_TABLE_INPUT.equals(((CompositeMap) c)
						.getName())) {
					continue;
				}
				String text = ((CompositeMap) c).getText();
				if (text != null && "".equals(text) == false) {
					CompositeMap child = pp.getChild(((CompositeMap) c)
							.getName());
					if (child == null) {
						child = pp.createChild(((CompositeMap) c).getName());
					}
					String t = child.getText();
					if (t == null || "".equals(t)) {
						child.setText(text);
					}
				}
			}
		}
	}

}
