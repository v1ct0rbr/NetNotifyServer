# NetNotifyServer

Propósito
- Backend responsável por autenticar usuários contra um serviço de diretório corporativo, emitir tokens de acesso e gerenciar envio/recebimento de notificações.

Funcionalidades
- Autenticação de usuários com verificação de pertença a grupos autorizados.
- Identificação de contas administrativas com base em grupo específico.
- Emissão de tokens de acesso após autenticação bem-sucedida.
- Publicação e consumo de mensagens em um broker de mensagens configurável.
- Persistência de mensagens em um armazenamento relacional e suporte a cache.
- Declaração automática de infraestrutura de mensagens (exchange/fila) na inicialização.

Execução
- Fornecer as variáveis de configuração via ambiente ou arquivo de propriedades.
- Gerar o artefato de distribuição do projeto (pacote executável).
- Executar o artefato no ambiente alvo. Na inicialização o serviço tentará preparar a infraestrutura de mensagens conforme a configuração.

Configuração
- Ajuste as variáveis de ambiente ou arquivo de configuração para:
  - Endereço e credenciais do serviço de diretório.
  - Grupos permitidos e grupo de administradores.
  - Endereço, credenciais, nome do exchange e fila do broker de mensagens.
  - Conexão do armazenamento persistente e do cache.
  - Chave e tempo de expiração para tokens.

Observações operacionais
- Se um componente de mensagens já existir com configuração diferente (por exemplo durabilidade), alinhe a configuração ou remova o recurso existente antes de iniciar o serviço.
- Valores sensíveis (senhas, chaves) devem ser mantidos fora do controle de versão e protegidos em execução.

Resumo
- Serviço configurável e orientado a integração: controla acesso por grupo, emite tokens e fornece um canal de notificações com persistência e cache.