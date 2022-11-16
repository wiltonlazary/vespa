package cmd

import (
	"fmt"
	"log"
	"os"
	"strings"
	"time"

	"github.com/pkg/browser"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/auth"
	"github.com/vespa-engine/vespa/client/go/auth/auth0"
)

// newLoginCmd runs the login flow guiding the user through the process
// by showing the login instructions, opening the browser.
// Use `expired` to run the login from other commands setup:
// this will only affect the messages.
func newLoginCmd(cli *CLI) *cobra.Command {
	return &cobra.Command{
		Use:               "login",
		Args:              cobra.NoArgs,
		Short:             "Authenticate the Vespa CLI",
		Example:           "$ vespa auth login",
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			ctx := cmd.Context()
			targetType, err := cli.config.targetType()
			if err != nil {
				return err
			}
			system, err := cli.system(targetType)
			if err != nil {
				return err
			}
			a, err := auth0.New(cli.config.authConfigPath(), system.Name, system.URL)
			if err != nil {
				return err
			}
			state, err := a.Authenticator.Start(ctx)
			if err != nil {
				return fmt.Errorf("could not start the authentication process: %w", err)
			}

			log.Printf("Your Device Confirmation code is: %s\n", state.UserCode)

			auto_open := confirm(cli, "Automatically open confirmation page in your default browser?")

			if auto_open {
				log.Printf("Opened link in your browser: %s\n", state.VerificationURI)
				err = browser.OpenURL(state.VerificationURI)
				if err != nil {
					log.Println("Couldn't open the URL, please do it manually")
				}
			} else {
				log.Printf("Please open link in your browser: %s\n", state.VerificationURI)
			}

			var res auth.Result
			err = cli.spinner(os.Stderr, "Waiting for login to complete in browser ...", func() error {
				res, err = a.Authenticator.Wait(ctx, state)
				return err
			})

			if err != nil {
				return fmt.Errorf("login error: %w", err)
			}

			log.Print("\n")
			log.Println("Successfully logged in.")
			log.Print("\n")

			// store the refresh token
			secretsStore := &auth.Keyring{}
			err = secretsStore.Set(auth.SecretsNamespace, system.Name, res.RefreshToken)
			if err != nil {
				// log the error but move on
				log.Println("Could not store the refresh token locally, please expect to login again once your access token expired.")
			}

			creds := auth0.Credentials{
				AccessToken: res.AccessToken,
				ExpiresAt:   time.Now().Add(time.Duration(res.ExpiresIn) * time.Second),
				Scopes:      auth.RequiredScopes(),
			}
			if err := a.WriteCredentials(creds); err != nil {
				return fmt.Errorf("failed to write credentials: %w", err)
			}
			return err
		},
	}
}

func confirm(cli *CLI, question string) bool {
	for {
		var answer string

		fmt.Fprintf(cli.Stdout, "%s [Y/n] ", question)
		fmt.Fscanln(cli.Stdin, &answer)

		answer = strings.TrimSpace(strings.ToLower(answer))

		if answer == "y" || answer == "" {
			return true
		} else if answer == "n" {
			return false
		} else {
			log.Printf("Please answer Y or N.\n")
		}
	}
}
