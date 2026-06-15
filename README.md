# 🏋️‍♂️ ShapeLog Bot - Telegram Fitness Assistant

O **ShapeLog Bot** é um assistente virtual inteligente construído para o Telegram. Ele utiliza a inteligência artificial do **Google Vertex AI (Gemini)** para compreender áudios e textos naturais, transformando relatos diários de alimentação e treinos em dados estruturados (calorias, macronutrientes, séries e cargas).

## 🚀 Status da Infraestrutura Atual

Este projeto foi arquitetado com foco total em isolamento, segurança e performance, rodando 100% em Nuvem:

- **Hospedagem:** Oracle Cloud Infrastructure (OCI).
- **Orquestração:** Docker & Docker Compose.
- **Banco de Dados:** PostgreSQL 15 rodando em container fechado.
- **Integração IA:** Google Vertex AI (Gemini 2.5 Flash) via Spring AI.

## ✨ Principais Funcionalidades

- 🎙️ **Reconhecimento de Áudio:** Envie um áudio no meio do treino ou após comer, e a IA extrai automaticamente os alimentos, estima calorias/macros e organiza as séries do treino.
- 📊 **Relatórios Diários:** Acompanhamento visual de Calorias, Proteínas, Carboidratos e Gorduras com barras de progresso comparadas à meta diária.
- 🔒 **Controle de Acesso (Whitelist):** Sistema de segurança robusto. O bot ignora completamente mensagens de usuários não autorizados.
- ✏️ **Edição Dinâmica:** Edição interativa através de "Inline Buttons" do Telegram, permitindo corrigir um exercício específico ou refeição sem precisar reescrever tudo.

## 📱 Telas e Resultados (Screenshots)

| Registro de Refeição via Áudio | Registro de Treino | Relatório Diário |
| :---: | :---: | :---: |
| ![Refeicao](https://via.placeholder.com/250x400?text=Print+Refeicao) | ![Treino](https://via.placeholder.com/250x400?text=Print+Treino) | ![Relatorio](https://via.placeholder.com/250x400?text=Print+Relatorio) |

<br>
*Exemplo prático: O usuário envia um áudio dizendo "Comi 2 ovos e 1 pão francês", o bot responde formatado e soma as calorias no banco de dados automaticamente.*

## 🛠️ Tecnologias Utilizadas

- **Java 17** + **Spring Boot 3**
- **Spring AI**
- **Spring Data JPA**
- **Telegrambots API**
- **PostgreSQL 15**
- **Docker**
