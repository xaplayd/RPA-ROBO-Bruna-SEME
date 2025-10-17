package application;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

import com.itextpdf.text.Document;
import com.itextpdf.text.pdf.PdfCopy;
import com.itextpdf.text.pdf.PdfImportedPage;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;

public class App {

	public static File pastaPontosOri = null;
	public static File pastaComprovantes = null;
	public static File pastaContracheque = null;

	private static JFrame frame;
	private static JTextArea consoleArea;
	private static JProgressBar progressBar;

	public static void main(String[] args) {
		criarJanelaConsole();

		SwingWorker<Void, Void> worker = new SwingWorker<>() {
			@Override
			protected Void doInBackground() {
				try {
					selecionarArquivosEDiretorios();

					if (pastaPontosOri == null || pastaComprovantes == null || pastaContracheque == null) {
						System.out.println("[ERRO] Arquivo de ponto ou pastas não selecionados. Encerrando.");
						return null;
					}

					File arquivoCompilado = new File(pastaPontosOri.getParent(), "COMPILACAO_FINAL.PDF");

					boolean status = true;

					if (arquivoCompilado.exists()) {
						status = confereStatus();
					}

					if (status == true) {
						separaPorPagina();
						renomeiaPontos();
						copiarArquivosCompilacao();
						List<Colaborador> pendentesComprovantes = revisaPendentesComprovantes(listarContracheques());
						List<Colaborador> pendentesPontos = revisaPendentesPontos(listarContracheques());

						if (!pendentesComprovantes.isEmpty() || !pendentesPontos.isEmpty()) {
							boolean compilarMesmo = perguntarCompilarComPendentes();
							if (!compilarMesmo) {
								System.out.println("\n[ERRO] Usuário optou por abortar a compilação.");
								System.out.println("\n[...] Excluindo arquivos para proxima tentativa de compilação");

								File pastaSeparados = new File(pastaPontosOri.getAbsolutePath() + "/separados");
								File pastaRenomeados = new File(pastaPontosOri.getAbsolutePath() + "/renomeados");
								File pastaCompilacao = new File(pastaComprovantes.getParent() + "/4_COMPILACAO");

								File[] pastas = { pastaSeparados, pastaRenomeados, pastaCompilacao };
								for (File pasta : pastas) {
									if (pasta.exists()) {
										File[] arquivos = pasta.listFiles();
										if (arquivos != null) {
											for (File f : arquivos) {
												f.delete();
											}
										}
										pasta.delete();
									}
								}
								for (File pasta : pastas) {
									if (pasta.exists()) {
										File[] arquivos = pasta.listFiles();
										if (arquivos != null) {
											for (File f : arquivos) {
												f.delete();
											}
										}
										pasta.delete();
									}
								}
								if (!pendentesComprovantes.isEmpty()) {
									System.out.println("\n\n\n[!] Comprovantes pendentes:");
									for (Colaborador c : pendentesComprovantes) {
										System.out.println(c.getNome() + " | " + c.getMatricula());
									}
								}
								if (!pendentesPontos.isEmpty()) {
									System.out.println("\n\n\n[!] Pontos pendentes:");
									for (Colaborador c : pendentesPontos) {
										System.out.println(c.getNome() + " | " + c.getMatricula());
									}
								}

								return null;
							}
						}

						juntarDocumentos();
					} else {
						System.out.println("\n[...] Abortado pelo usuário!");
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				return null;
			}

			@Override
			protected void done() {
				progressBar.setIndeterminate(false);
				progressBar.setString("[OK] Concluído!");
				frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
				System.out.println("\n[OK] Processo finalizado!");
			}
		};

		worker.execute();
	}

	private static void criarJanelaConsole() {
		frame = new JFrame("Processamento de Documentos");
		frame.setSize(500, 300);
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.setLayout(new BorderLayout());

		consoleArea = new JTextArea();
		consoleArea.setEditable(false);
		consoleArea.setBackground(Color.BLACK);
		consoleArea.setForeground(Color.WHITE);
		consoleArea.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
		consoleArea.setLineWrap(true);
		consoleArea.setWrapStyleWord(true);

		JScrollPane scrollPane = new JScrollPane(consoleArea);
		scrollPane.getVerticalScrollBar().setBackground(Color.DARK_GRAY);

		progressBar = new JProgressBar();
		progressBar.setIndeterminate(true);
		progressBar.setString("Processando...");
		progressBar.setStringPainted(true);
		progressBar.setForeground(new Color(0, 180, 0));
		progressBar.setBackground(Color.BLACK);

		frame.add(scrollPane, BorderLayout.CENTER);
		frame.add(progressBar, BorderLayout.SOUTH);

		try {
			PrintStream printStream = new PrintStream(new OutputStream() {
				@Override
				public void write(int b) {

					char c = (char) (b & 0xFF);
					SwingUtilities.invokeLater(() -> {
						consoleArea.append(String.valueOf(c));
						consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
					});
				}
			}, true, "ISO-8859-1");
			System.setOut(printStream);
			System.setErr(printStream);
		} catch (Exception e) {
			e.printStackTrace();
		}

		frame.setVisible(true);
	}

	public static void selecionarArquivosEDiretorios() {
		pastaPontosOri = selecionarDiretorio("Selecione a pasta de PONTOS");
		pastaComprovantes = selecionarDiretorio("Selecione a pasta de COMPROVANTES");
		pastaContracheque = selecionarDiretorio("Selecione a pasta de CONTRACHEQUES");
	}

	public static File selecionarArquivo(String mensagem) {

		UIManager.put("FileChooser.openDialogTitleText", "Abrir");
		UIManager.put("FileChooser.saveDialogTitleText", "Salvar");
		UIManager.put("FileChooser.cancelButtonText", "Cancelar");
		UIManager.put("FileChooser.openButtonText", "Abrir");
		UIManager.put("FileChooser.saveButtonText", "Salvar");
		UIManager.put("FileChooser.filesOfTypeLabelText", "Tipo de arquivo");
		UIManager.put("FileChooser.lookInLabelText", "Procurar em");
		UIManager.put("FileChooser.fileNameLabelText", "Nome do arquivo");
		JFileChooser chooser = new JFileChooser();
		chooser.setCurrentDirectory(new File("//adsj/files$/FINANCEIRO/2.FATURAMENTO/SUPORTE.TI/Documentação SEME"));
		chooser.setDialogTitle(mensagem);
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		int resultado = chooser.showOpenDialog(null);
		if (resultado == JFileChooser.APPROVE_OPTION) {
			File arquivo = chooser.getSelectedFile();
			System.out.println("\n[OK] Arquivo selecionado: " + arquivo.getAbsolutePath());
			return arquivo;
		} else {
			System.out.println("[ERRO] Nenhum arquivo selecionado.");
			return null;
		}
	}

	public static File selecionarDiretorio(String mensagem) {
		UIManager.put("FileChooser.openDialogTitleText", "Abrir");
		UIManager.put("FileChooser.saveDialogTitleText", "Salvar");
		UIManager.put("FileChooser.cancelButtonText", "Cancelar");
		UIManager.put("FileChooser.openButtonText", "Abrir");
		UIManager.put("FileChooser.saveButtonText", "Salvar");
		UIManager.put("FileChooser.filesOfTypeLabelText", "Tipo de arquivo");
		UIManager.put("FileChooser.lookInLabelText", "Procurar em");
		UIManager.put("FileChooser.fileNameLabelText", "Nome do arquivo");
		JFileChooser chooser = new JFileChooser();
		chooser.setCurrentDirectory(new File("//adsj/files$/FINANCEIRO/2.FATURAMENTO/SUPORTE.TI/Documentação SEME"));
		chooser.setDialogTitle(mensagem);
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		int resultado = chooser.showOpenDialog(null);
		if (resultado == JFileChooser.APPROVE_OPTION) {
			File pasta = chooser.getSelectedFile();
			System.out.println("\n[OK] Pasta selecionada: " + pasta.getAbsolutePath());
			return pasta;
		} else {
			System.out.println("[ERRO] Nenhuma pasta selecionada.");
			return null;
		}
	}

	public static void renomeiaPontos() {

		try {

			File pastaOrigem = new File(pastaPontosOri.getAbsolutePath() + "/separados");
			File pastaDestino = new File(pastaPontosOri.getAbsolutePath() + "/renomeados");

			if (!pastaDestino.exists()) {
				pastaDestino.mkdirs();
			}

			File[] pdfs = pastaOrigem.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));

			if (pdfs == null || pdfs.length == 0) {
				System.out.println("\n[ERRO] Nenhum PDF encontrado na pasta de origem!");
				return;
			}

			System.out.println("\n[...] Iniciando processamento de " + pdfs.length + " arquivos...");

			for (File arquivo : pdfs) {
				System.out.println("\n[...] Lendo: " + arquivo.getName());

				try {

					PdfReader reader = new PdfReader(arquivo.getAbsolutePath());
					StringBuilder textoPdf = new StringBuilder();

					for (int i = 1; i <= reader.getNumberOfPages(); i++) {
						textoPdf.append(PdfTextExtractor.getTextFromPage(reader, i)).append("\n");
					}
					reader.close();

					List<String> linhas = List.of(textoPdf.toString().split("\\r?\\n"));

					if (linhas.size() < 5) {
						System.out.println("[ERRO] O PDF não possui linhas suficientes!");
						continue;
					}

					String linhaNome = linhas.get(4).trim();
					String nome = "";
					String matricula = "";

					int idxMatricula = linhaNome.toUpperCase().indexOf("MATRÍCULA");
					if (idxMatricula == -1) {
						idxMatricula = linhaNome.toUpperCase().indexOf("MATRÍCULA");
					}

					if (idxMatricula > 12) {
						nome = linhaNome.substring(12, idxMatricula).trim();
					}

					if (idxMatricula != -1) {
						int startMat = idxMatricula + "MATRÍCULA".length();

						while (startMat < linhaNome.length()
								&& (linhaNome.charAt(startMat) == ' ' || linhaNome.charAt(startMat) == ':')) {
							startMat++;
						}

						int endMat = Math.min(startMat + 9, linhaNome.length());
						matricula = linhaNome.substring(startMat, endMat).trim();
					}

					if (nome.isEmpty() || matricula.isEmpty()) {
						System.out.println("[ERRO] Nome ou matrícula não encontrados!");
						System.out.println("Linha analisada: " + linhaNome);
						continue;
					}

					String novoNome = nome + " " + matricula + " 1_Ponto.pdf";
					novoNome = novoNome.replaceAll("[\\\\/:*?\"<>|]", "");

					File destino = new File(pastaDestino, novoNome);
					Files.copy(arquivo.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING);

					System.out.println("[OK] Renomeado para: " + novoNome);

				} catch (Exception eArq) {
					System.out.println("[ERRO] Erro ao processar " + arquivo.getName());
					eArq.printStackTrace();
				}
			}

			System.out.println("\n[OK] Renomeação dos arquivos concluído com sucesso!");

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void separaPorPagina() {
		System.out.println("\n[...] Separando todos os PDFs da pasta por página!");

		try {

			File pastaOrigem = pastaPontosOri;
			File[] arquivos = pastaOrigem.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));

			if (arquivos == null || arquivos.length == 0) {
				System.out.println("[AVISO] Nenhum arquivo PDF encontrado em: " + pastaOrigem.getAbsolutePath());
				return;
			}

			File pastaDestinoGeral = new File(pastaOrigem.getAbsolutePath() + "/separados");
			if (!pastaDestinoGeral.exists()) {
				pastaDestinoGeral.mkdirs();
			}
			Integer qtd = 1;

			for (File arquivoPdf : arquivos) {
				System.out.println("\n[INFO] Processando: " + arquivoPdf.getName());

				PdfReader reader = null;
				try {
					reader = new PdfReader(arquivoPdf.getAbsolutePath());
					int totalPages = reader.getNumberOfPages();

					String nomeBase = arquivoPdf.getName().replaceAll("(?i)\\.pdf$", "");

					for (int i = 1; i <= totalPages; i++) {
						String outputFile = pastaDestinoGeral.getAbsolutePath() + "/pg_" + qtd + ".pdf";

						Document document = null;
						PdfCopy copy = null;
						try {
							document = new Document();
							copy = new PdfCopy(document, new FileOutputStream(outputFile));
							document.open();

							PdfImportedPage page = copy.getImportedPage(reader, i);
							copy.addPage(page);

							System.out.println("[OK] " + nomeBase + " - Página " + i + " salva.");
							qtd++;

						} catch (Exception ex) {
							System.err.println(
									"[ERRO] Falha ao salvar página " + i + " de " + nomeBase + ": " + ex.getMessage());
							ex.printStackTrace();
						} finally {
							if (document != null && document.isOpen()) {
								document.close();
							}
							if (copy != null) {
								copy.close();
							}
						}
					}

					System.out.println(
							"[SUCESSO] Arquivo " + arquivoPdf.getName() + " dividido em " + totalPages + " páginas.");

				} catch (Exception e) {
					System.err.println("[ERRO] Falha ao processar " + arquivoPdf.getName() + ": " + e.getMessage());
					e.printStackTrace();
				} finally {
					if (reader != null) {
						reader.close();
					}
				}
			}

			System.out.println("\n[OK] Todos os PDFs foram separados por página com sucesso!");

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static void copiarArquivosCompilacao() {
		System.out.println("\n[...] Copiando arquivos para pasta prévia de compilação 4_COMPILACAO!");
		File pastaPontos = new File(pastaPontosOri.getAbsolutePath() + "/renomeados");
		File pastaCompilacao = new File(pastaComprovantes.getParent() + "/4_COMPILACAO");

		if (!pastaCompilacao.exists())
			pastaCompilacao.mkdirs();

		File[] todasPastas = { pastaPontos, pastaComprovantes, pastaContracheque };

		for (File pasta : todasPastas) {
			File[] arquivos = pasta.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
			if (arquivos == null)
				continue;

			for (File f : arquivos) {
				try {
					File destino = new File(pastaCompilacao, f.getName());
					Files.copy(f.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING);
				} catch (Exception e) {
					System.out.println("[ERRO] Erro ao copiar arquivo: " + f.getName());
				}
			}
		}

		System.out.println("\n[OK] Todos os arquivos foram copiados para a pasta de compilação.");
	}

	public static List<Colaborador> listarContracheques() {
		List<Colaborador> listContracheque = new ArrayList<Colaborador>();
		try {

			File[] arquivos = pastaContracheque.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
			if (arquivos == null || arquivos.length == 0) {
				System.out.println("[ERRO] Nenhum contracheque encontrado!");
				return null;
			}

			for (File f : arquivos) {

				String nomeArquivo = f.getName().replace(".pdf", "").trim();

				// encontra o primeiro dígito
				int posPrimeiroNumero = -1;
				for (int i = 0; i < nomeArquivo.length(); i++) {
					if (Character.isDigit(nomeArquivo.charAt(i))) {
						posPrimeiroNumero = i;
						break;
					}
				}

				if (posPrimeiroNumero == -1 || posPrimeiroNumero < 2) {
					System.out.println("[ERRO] Arquivo fora do padrão: " + nomeArquivo);
					continue;
				}

				// Nome = tudo até 2 caracteres antes do primeiro número
				String nome = nomeArquivo.substring(0, posPrimeiroNumero - 1).trim();

				// Matrícula = 9 caracteres a partir do primeiro número
				int fimMatricula = Math.min(posPrimeiroNumero + 9, nomeArquivo.length());
				String matricula = nomeArquivo.substring(posPrimeiroNumero, fimMatricula);

				// Tipo documento = o restante depois da matrícula
				String tipo = "";
				if (fimMatricula < nomeArquivo.length()) {
					tipo = nomeArquivo.substring(fimMatricula).trim();
				}
				Colaborador tmpColab = new Colaborador(nome, matricula);
				listContracheque.add(tmpColab);

			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return listContracheque;
	}

	public static List<Colaborador> revisaPendentesComprovantes(List<Colaborador> tmpList) {
		System.out.println("\n[...] Iniciando revisão de comprovantes pendentes!");

		List<Colaborador> listComprovantes = new ArrayList<Colaborador>();
		List<Colaborador> listComprovantesPendentes = new ArrayList<Colaborador>();
		try {

			File[] arquivos = pastaComprovantes.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
			if (arquivos == null || arquivos.length == 0) {
				System.out.println("[ERRO] Nenhum comprovante encontrado!");
				return null;
			}

			for (File f : arquivos) {

				String nomeArquivo = f.getName().replace(".pdf", "").trim();

				// encontra o primeiro dígito
				int posPrimeiroNumero = -1;
				for (int i = 0; i < nomeArquivo.length(); i++) {
					if (Character.isDigit(nomeArquivo.charAt(i))) {
						posPrimeiroNumero = i;
						break;
					}
				}

				if (posPrimeiroNumero == -1 || posPrimeiroNumero < 2) {
					System.out.println("[ERRO] Arquivo fora do padrão: " + nomeArquivo);
					continue;
				}

				// Nome = tudo até 2 caracteres antes do primeiro número
				String nome = nomeArquivo.substring(0, posPrimeiroNumero - 1).trim();

				// Matrícula = 9 caracteres a partir do primeiro número
				int fimMatricula = Math.min(posPrimeiroNumero + 9, nomeArquivo.length());
				String matricula = nomeArquivo.substring(posPrimeiroNumero, fimMatricula);

				// Tipo documento = o restante depois da matrícula
				String tipo = "";
				if (fimMatricula < nomeArquivo.length()) {
					tipo = nomeArquivo.substring(fimMatricula).trim();
				}
				Colaborador tmpColab = new Colaborador(nome, matricula);
				listComprovantes.add(tmpColab);

			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		for (Colaborador x : tmpList) {
			boolean encontrado = false;
			for (Colaborador comprovante : listComprovantes) {
				if (x.getMatricula().equals(comprovante.getMatricula())) {
					encontrado = true;
					break;
				}
			}
			if (!encontrado) {
				// Não encontrou comprovante, adiciona na lista de pendentes
				listComprovantesPendentes.add(x);
			}
		}

		// Exibe pendentes
		System.out.println("[...] Comprovantes pendentes:");
		for (Colaborador c : listComprovantesPendentes) {
			System.out.println(c.getNome() + " | " + c.getMatricula());
		}

		return listComprovantesPendentes;

	}

	public static List<Colaborador> revisaPendentesPontos(List<Colaborador> tmpList) {
		System.out.println("\n[...] Iniciando revisão de pontos pendentes!");
		List<Colaborador> listPontos = new ArrayList<Colaborador>();
		List<Colaborador> listPontosPendentes = new ArrayList<Colaborador>();
		try {
			File pastaPonto = new File(pastaPontosOri.getAbsolutePath() + "/renomeados");

			File[] arquivos = pastaPonto.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
			if (arquivos == null || arquivos.length == 0) {
				System.out.println("[ERRO] Nenhum ponto encontrado!");
				return null;
			}

			for (File f : arquivos) {

				String nomeArquivo = f.getName().replace(".pdf", "").trim();

				// encontra o primeiro dígito
				int posPrimeiroNumero = -1;
				for (int i = 0; i < nomeArquivo.length(); i++) {
					if (Character.isDigit(nomeArquivo.charAt(i))) {
						posPrimeiroNumero = i;
						break;
					}
				}

				if (posPrimeiroNumero == -1 || posPrimeiroNumero < 2) {
					System.out.println("[ERRO] Arquivo fora do padrão: " + nomeArquivo);
					continue;
				}

				// Nome = tudo até 2 caracteres antes do primeiro número
				String nome = nomeArquivo.substring(0, posPrimeiroNumero - 1).trim();

				// Matrícula = 9 caracteres a partir do primeiro número
				int fimMatricula = Math.min(posPrimeiroNumero + 9, nomeArquivo.length());
				String matricula = nomeArquivo.substring(posPrimeiroNumero, fimMatricula);

				// Tipo documento = o restante depois da matrícula
				String tipo = "";
				if (fimMatricula < nomeArquivo.length()) {
					tipo = nomeArquivo.substring(fimMatricula).trim();
				}
				Colaborador tmpColab = new Colaborador(nome, matricula);
				listPontos.add(tmpColab);

			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		for (Colaborador x : tmpList) {
			boolean encontrado = false;
			for (Colaborador ponto : listPontos) {
				if (x.getMatricula().equals(ponto.getMatricula())) {
					encontrado = true;
					break;
				}
			}
			if (!encontrado) {
				// Não encontrou comprovante, adiciona na lista de pendentes
				listPontosPendentes.add(x);
			}
		}

		// Exibe pendentes
		System.out.println("[...] Pontos pendentes:");
		for (Colaborador c : listPontosPendentes) {
			System.out.println(c.getNome() + " | " + c.getMatricula());
		}

		return listPontosPendentes;

	}

	public static boolean perguntarCompilarComPendentes() {

		UIManager.put("OptionPane.yesButtonText", "Sim");
		UIManager.put("OptionPane.noButtonText", "Abortar");
		int opcao = JOptionPane.showConfirmDialog(frame, // Janela pai
				"Existem documentos pendentes. Deseja compilar mesmo assim?", "Confirmação", JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE);
		return opcao == JOptionPane.YES_OPTION; // true = continuar, false = abortar
	}

	public static void juntarDocumentos() {
		System.out.println("\n[...] Iniciando compilação do arquivo final!");
		try {
			File pastaCompilacao = new File(pastaComprovantes.getParent() + "/4_COMPILACAO");
			File[] arquivos = pastaCompilacao.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));

			if (arquivos == null || arquivos.length == 0) {
				System.out.println("[ERRO] Nenhum PDF encontrado para juntar!");
				return;
			}

			// Ordena pelo NOME do arquivo
			java.util.Arrays.sort(arquivos, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

			// Arquivo de saída
			File output = new File(pastaCompilacao.getParentFile(), "COMPILACAO_FINAL.pdf");
			System.out.println("[...] Criando arquivo final: " + output.getAbsolutePath());

			com.itextpdf.text.Document document = new com.itextpdf.text.Document();
			PdfCopy copy = new PdfCopy(document, new FileOutputStream(output));
			document.open();

			for (File pdf : arquivos) {
				PdfReader reader = new PdfReader(pdf.getAbsolutePath());
				int nPaginas = reader.getNumberOfPages();
				for (int i = 1; i <= nPaginas; i++) {
					copy.addPage(copy.getImportedPage(reader, i));
				}
				reader.close();
				System.out.println("[OK] Adicionado: " + pdf.getName());
			}

			document.close();
			System.out.println("\n[OK] Todos os PDFs foram compilados com sucesso!");
		} catch (Exception e) {
			System.out.println("[ERRO] Erro ao juntar documentos!");
			e.printStackTrace();
		}
		File pastaOrigem = new File(pastaPontosOri.getAbsolutePath() + "/separados");
		System.out.println("\n[...] Iniciando Exclusão de arquivos temporários!");
		System.gc();
		if (pastaOrigem.exists()) {
			File[] arquivos = pastaOrigem.listFiles();
			if (arquivos != null) {
				for (File f : arquivos) {
					f.delete();
				}
			}
			pastaOrigem.delete();
			System.out.println("[OK] Pasta 'separados' excluída com sucesso!");
		}
	}

	public static boolean confereStatus() {

		UIManager.put("OptionPane.yesButtonText", "Sim");
		UIManager.put("OptionPane.noButtonText", "Abortar");

		int opcao = JOptionPane.showConfirmDialog(frame,
				"Já existe uma compilação executada. Deseja executar mesmo assim?\n                                       (O arquivo será sobreposto)",
				"Confirmação", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		return opcao == JOptionPane.YES_OPTION;
	}
}

class Colaborador {
	String nome;
	String matricula;

	Colaborador(String nome, String matricula) {
		this.nome = nome;
		this.matricula = matricula;
	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public String getMatricula() {
		return matricula;
	}

	public void setMatricula(String matricula) {
		this.matricula = matricula;
	}

	@Override
	public int hashCode() {
		return Objects.hash(matricula, nome);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Colaborador other = (Colaborador) obj;
		return Objects.equals(matricula, other.matricula) && Objects.equals(nome, other.nome);
	}

	@Override
	public String toString() {
		return "Colaborador [nome=" + nome + ", matricula=" + matricula + "]";
	}

}
