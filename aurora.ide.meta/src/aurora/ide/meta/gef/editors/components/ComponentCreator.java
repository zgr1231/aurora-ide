package aurora.ide.meta.gef.editors.components;

import org.eclipse.gef.EditPart;
import org.eclipse.gef.palette.PaletteEntry;

import aurora.plugin.source.gen.screen.model.AuroraComponent;

public class ComponentCreator {
	
	public PaletteEntry createPaletteEntry(){
		return null;
	}
	public EditPart createEditPart(Object model){
		return null;
	}
	public Class<? extends AuroraComponent> clazz() {
		return null;
	}
	public AuroraComponent createComponent(String type){
		return null;
	}
}
