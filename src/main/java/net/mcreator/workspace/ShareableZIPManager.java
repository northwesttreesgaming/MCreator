/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2020 Pylo and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.mcreator.workspace;

import net.mcreator.element.GeneratableElement;
import net.mcreator.io.zip.ZipIO;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.dialogs.ProgressDialog;
import net.mcreator.ui.init.L10N;
import net.mcreator.workspace.elements.ModElement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class ShareableZIPManager {

	private static final Logger LOG = LogManager.getLogger("Shareable ZIP Manager");

	public static File importZIP(File file, File workspaceDir, Window window) {
		AtomicReference<File> retval = new AtomicReference<>();

		ProgressDialog dial = new ProgressDialog(window, L10N.t("dialog.workspace.import_from_zip.importing"));

		Thread t = new Thread(() -> {
			ProgressDialog.ProgressUnit p1 = new ProgressDialog.ProgressUnit(
					L10N.t("dialog.workspace.import_from_zip.extracting"));
			dial.addProgress(p1);

			ZipIO.unzip(file.getAbsolutePath(), workspaceDir.getAbsolutePath());

			retval.set(WorkspaceUtils.getWorkspaceFileForWorkspaceFolder(workspaceDir));

			if (retval.get() != null) {
				p1.ok();
				dial.refreshDisplay();
			} else {
				p1.err();
				dial.refreshDisplay();

				JOptionPane.showMessageDialog(dial, L10N.t("dialog.workspace.import_from_zip.failed_message"),
						L10N.t("dialog.workspace.import_from_zip.failed_title"), JOptionPane.ERROR_MESSAGE);

				dial.hideAll();

				return;
			}

			ProgressDialog.ProgressUnit p2 = new ProgressDialog.ProgressUnit(
					L10N.t("dialog.workspace.regenerate_and_build.progress.loading_mod_elements"));
			dial.addProgress(p2);

			try {
				Workspace workspace = Workspace.readFromFS(retval.get(), dial);

				int modstoload = workspace.getModElements().size();

				int i = 0;
				// In exported ZIPs, mod element images are not stored, so we regenerate them here
				// If workspace MCR version is the same, regeneration will not run and thus ME icons will be missing
				// This is "fixed" by "preloading mod elements" here
				for (ModElement mod : workspace.getModElements()) {
					GeneratableElement generatableElement = mod.getGeneratableElement();

					if (generatableElement != null) {
						workspace.getModElementManager().storeModElementPicture(
								generatableElement); // save custom mod element picture if it has one
						workspace.addModElement(
								generatableElement.getModElement()); // add mod element to workspace again, so the icons get reloaded
						generatableElement.getModElement()
								.reinit(workspace); // we reinit the mod to load new icons etc.
					}

					i++;
					p1.setPercent((int) (((float) i / (float) modstoload) * 100.0f));
					dial.refreshDisplay();
				}

				workspace.close(); // we need to close the workspace!
			} catch (Exception e) {
				e.printStackTrace();
			}

			p2.ok();
			dial.refreshDisplay();

			dial.hideAll();
		});
		t.start();
		dial.setVisible(true);

		return retval.get();
	}

	public static void exportZIP(String title, File file, MCreator mcreator, boolean excludeRunDir) {
		ProgressDialog dial = new ProgressDialog(mcreator, title);
		Thread t = new Thread(() -> {
			ProgressDialog.ProgressUnit p1 = new ProgressDialog.ProgressUnit(
					L10N.t("dialog.workspace.export_workspace.compressing"));
			dial.addProgress(p1);

			try {
				if (excludeRunDir) {
					ZipIO.zipDir(mcreator.getWorkspaceFolder().getAbsolutePath(), file.getAbsolutePath(), ".gradle/",
							".mcreator/", "build/", "gradle/", "run/", "#build.gradle", "#gradlew", "#gradlew.bat",
							"#mcreator.gradle", ".git/", "#.classpath", "#.project", ".idea/", ".settings/");
				} else {
					ZipIO.zipDir(mcreator.getWorkspaceFolder().getAbsolutePath(), file.getAbsolutePath(), ".gradle/",
							".mcreator/", "build/", "gradle/", "#build.gradle", "#gradlew", "#gradlew.bat",
							"#mcreator.gradle", ".git/", "#.classpath", "#.project", ".idea/", ".settings/");
				}
			} catch (IOException e) {
				LOG.error(e.getMessage(), e);
			}

			p1.ok();
			dial.refreshDisplay();
			dial.hideAll();
		});
		t.start();
		dial.setVisible(true);
	}

}
