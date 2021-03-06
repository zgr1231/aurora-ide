package aurora.ide.meta.gef.editors.wizard.dialog;

import java.io.File;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.gef.EditPartFactory;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import aurora.ide.meta.gef.control.ConsultantDemonstratingComposite;
import aurora.ide.meta.gef.editors.EditorMode;
import aurora.ide.meta.gef.editors.parts.ExtSysLovAuroraPartFactory;
import aurora.ide.prototype.consultant.demonstrate.LOVDemonstrating;
import aurora.plugin.source.gen.screen.model.AuroraComponent;
import aurora.plugin.source.gen.screen.model.Combox;
import aurora.plugin.source.gen.screen.model.DatePicker;
import aurora.plugin.source.gen.screen.model.DemonstrateBind;
import aurora.plugin.source.gen.screen.model.Form;
import aurora.plugin.source.gen.screen.model.Grid;
import aurora.plugin.source.gen.screen.model.GridColumn;
import aurora.plugin.source.gen.screen.model.HBox;
import aurora.plugin.source.gen.screen.model.Input;
import aurora.plugin.source.gen.screen.model.LOV;
import aurora.plugin.source.gen.screen.model.NumberField;
import aurora.plugin.source.gen.screen.model.ScreenBody;
import aurora.plugin.source.gen.screen.model.TextField;
import aurora.plugin.source.gen.screen.model.properties.ComponentInnerProperties;
import aurora.plugin.source.gen.screen.model.properties.ComponentProperties;

public class DemonstratingDialog extends Dialog {

	private LovDialogInput input;
	private LOVDemonstrating demon;
	private File project;

	public DemonstratingDialog(Shell parentShell, LOVDemonstrating demon) {
		super(parentShell);
		this.demon = demon;
	}

	protected Point getInitialSize() {
		return new Point(500, 530);
	}

	@Override
	protected Button createButton(Composite parent, int id, String label,
			boolean defaultButton) {
		return super.createButton(parent, id, label, defaultButton);
	}

	@Override
	protected Control createButtonBar(Composite parent) {
		return super.createButtonBar(parent);
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		// super.createButtonsForButtonBar(parent);
	}

	@Override
	protected Control createContents(Composite parent) {
		return super.createContents(parent);
	}

	protected Control createDialogArea(Composite parent) {
		Composite container = (Composite) super.createDialogArea(parent);
		container.setLayout(new GridLayout());
		ConsultantDemonstratingComposite vsEditor = new ConsultantDemonstratingComposite(
				this);
		ScreenBody viewDiagram = new ScreenBody();
		viewDiagram.addChild(createForm());
		viewDiagram.addChild(createButtons());
		viewDiagram.addChild(createGrid());
		vsEditor.setInput(viewDiagram);
		vsEditor.createPartControl(container);
		vsEditor.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));
		return container;
	}

	protected HBox createButtons() {
		HBox hb = new HBox();
		aurora.plugin.source.gen.screen.model.Button child = new aurora.plugin.source.gen.screen.model.Button();
		child.setText("查询");
		hb.addChild(child);
		child = new aurora.plugin.source.gen.screen.model.Button();
		child.setText("确定");
		hb.addChild(child);
		return hb;
	}

	public Form createForm() {
		Form form = new Form();
		form.setSize(450, 85);
		form.setCol(2);
		if (input !=null){
			List<DemonstrateBind> bindModels = input.getBindModels();
			if (bindModels != null)
				for (DemonstrateBind db : bindModels) {
					String editor = db.getForQueryEditor();
					if ("".equals(editor) == false) {
						AuroraComponent createInput = createInput(db);
						if (createInput != null)
							form.addChild(createInput);
					}
				}
		}
		
		// AuroraComponent tf0 = createTextField(0);
		// if (tf0 != null)
		// form.addChild(tf0);
		// AuroraComponent cb1 = createCombox(1);
		// if (cb1 != null)
		// form.addChild(cb1);
		// AuroraComponent tf2 = createTextField(2);
		// if (tf2 != null)
		// form.addChild(tf2);
		// AuroraComponent cb3 = createCombox(3);
		// if (cb3 != null)
		// form.addChild(cb3);
		return form;
	}

	private AuroraComponent createInput(DemonstrateBind db) {
		AuroraComponent ac = null;
		if (Input.TEXT.equals(db.getForQueryEditor())) {
			ac = new TextField();
		}
		if (Input.NUMBER.equals(db.getForQueryEditor())) {
			ac = new NumberField();
		}
		if (Input.Combo.equals(db.getForQueryEditor())) {
			ac = new Combox();
		}
		if (Input.LOV.equals(db.getForQueryEditor())) {
			ac = new LOV();
		}
		if (Input.DATE_PICKER.equals(db.getForQueryEditor())) {
			ac = new DatePicker();
		}
		if (ac != null) {
			ac.setPrompt(db.getColumnPrompt());
		}
		return ac;
	}

	private AuroraComponent createTextField(int i) {
		TextField textField = new TextField();
		return cf(textField, i);
	}

	private AuroraComponent cf(AuroraComponent ac, int i) {
		if (input != null) {
			if (i >= input.getCol()) {
				return null;
			}
			String name = input.get(i, 0);
			String value = input.get(i, 1);
			ac.setPropertyValue(ComponentProperties.prompt, name);
			ac.setPropertyValue(ComponentInnerProperties.INPUT_SIMPLE_DATA,
					value);
		}
		return ac;
	}

	private AuroraComponent createCombox(int i) {
		Combox combox = new Combox();
		// if (input != null) {
		// String head = input.getHead(i);
		// if (head != null) {
		// combox.setPropertyValue(
		// ComponentInnerProperties.INPUT_SIMPLE_DATA, head);
		// } else {
		// return null;
		// }
		// }
		return cf(combox, i);
	}

	public Grid createGrid() {
		Grid grid = new Grid();
		grid.setSize(450, 300);
		if (input == null || input.getCol() == 0) {
			grid.addCol(new GridColumn());
			grid.addCol(new GridColumn());
			grid.addCol(new GridColumn());
		} else {
			List<DemonstrateBind> bindModels = input.getBindModels();
			if (bindModels != null)
				for (int i = 0; i < bindModels.size(); i++) {
					DemonstrateBind db = bindModels.get(i);
					if (db.isForDisplay() == false) {
						continue;
					}
					GridColumn cc = createGridColumn(i);
					if (cc != null) {
						cc.setPrompt(db.getColumnPrompt());
						grid.addChild(cc);
					}
				}

			// for (int i = 0; i < 6; i++) {
			// GridColumn cc = createGridColumn(i);
			// if (cc != null) {
			// grid.addChild(cc);
			// }
			// }
		}
		grid.setPropertyValue(ComponentProperties.navBarType,
				Grid.NAVBAR_COMPLEX);
		return grid;
	}

	public GridColumn createGridColumn(int i) {
		GridColumn gridColumn = new GridColumn();
		if (input != null && input.getCol() > 0) {
			int row = input.getRow();
			if (i >= input.getCol()) {
				return null;
			} else {
				int max = row < 9 ? row : 9;
				for (int j = 0; j < (max); j++) {
					String s = input.get(i, j);
					if (s == null)
						break;
					// if (j == 0) {
					// gridColumn.setPropertyValue(ComponentProperties.prompt,
					// s);
					// } else {
					gridColumn.setPropertyValue(
							ComponentInnerProperties.GRID_COLUMN_SIMPLE_DATA
									+ (j + 1), s);
					// }
				}
			}
		}
		return gridColumn;
	}

	public void setInput(Object input) {
		this.input = (LovDialogInput) input;
	}

	public void applyValue(String value) {
		demon.applyValue(value);
		this.close();
	}

	public EditPartFactory getPartFactory(EditorMode editorMode) {
		return new ExtSysLovAuroraPartFactory(editorMode);
	}

	public IPath getActiveFilePath() {
		return new Path("");
	}

	public File getProject() {
		return project;
	}

	public void setProject(File project) {
		this.project = project;
	}

	public void applyValue(String value, int idx) {
		if(input == null)
			return;
		List<DemonstrateBind> bindModels = input.getBindModels();
		if (bindModels != null) {
			for (int i = 0; i < bindModels.size(); i++) {
				DemonstrateBind db = bindModels.get(i);
				AuroraComponent ac = db.getBindModel();
				if (ac == null)
					continue;
				demon.applyValue(ac, input.get(i, idx - 1));
			}
		}
		applyValue(value);
	}

}
